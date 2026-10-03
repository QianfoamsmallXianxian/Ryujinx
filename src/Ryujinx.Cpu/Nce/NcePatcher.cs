using Ryujinx.Cpu.Nce.Arm64;
using Ryujinx.Common;
using Ryujinx.Common.Logging;
using System;
using System.Runtime.InteropServices;

namespace Ryujinx.Cpu.Nce
{
    public static class NcePatcher
    {
        private const int ScratchBaseReg = 19;
        private const int LinuxUContextSpOffset = 432;
        private const int LinuxUContextPcOffset = 440;
        private const int LinuxUContextPstateOffset = 448;
        private const int DarwinUContextMContextOffset = 48;
        private const int DarwinMContextSpOffset = 264;
        private const int DarwinMContextPcOffset = 272;

        private const uint PstateNzcvMask = 0xf0000000u;

        private const uint IntCalleeSavedRegsMask = 0x1ff80000; // X19 to X28
        private const uint FpCalleeSavedRegsMask = 0xff00; // D8 to D15

        internal static bool UsesThreadContextIndex => PlatformInfo.IsBionic;

        public static NceCpuCodePatch CreatePatch(ReadOnlySpan<byte> textSection)
        {
            NceCpuCodePatch codePatch = new();

            ReadOnlySpan<uint> textUint = MemoryMarshal.Cast<byte, uint>(textSection);

            int orderedExclusives = 0;

            for (int i = 0; i < textUint.Length; i++)
            {
                uint inst = textUint[i];
                ulong address = (ulong)i * sizeof(uint);

                uint systemRead = inst & ~0x1fu;

                // MRS uses register 31 as XZR. The context-read helper uses ADD-immediate,
                // where destination 31 is SP; do not generate that helper for a discarded result.
                // These emulated, read-only registers have no guest-visible read side effects.
                if ((inst & 0x1fu) == 31u &&
                    (systemRead == 0xd53bd060u || systemRead == 0xd53bd040u ||
                     systemRead == 0xd53be020u ||
                     (systemRead == 0xd53b0020u && OperatingSystem.IsMacOS())))
                {
                    codePatch.ReplaceInstruction(i, 0xd503201fu); // NOP
                }
                else if ((inst & ~(0xffffu << 5)) == 0xd4000001u) // svc #0
                {
                    uint svcId = (ushort)(inst >> 5);
                    codePatch.AddCode(i, WriteSvcPatch(svcId), inst);
                    Logger.Debug?.Print(LogClass.Cpu, $"Patched SVC #{svcId} at 0x{address:X}.");
                }
                else if ((inst & ~0x1f) == 0xd53bd060) // mrs x0, tpidrro_el0
                {
                    uint rd = inst & 0x1f;
                    codePatch.AddCode(i, WriteMrsTpidrroEl0Patch(rd), inst);
                    Logger.Debug?.Print(LogClass.Cpu, $"Patched MRS x{rd}, tpidrro_el0 at 0x{address:X}.");
                }
                else if ((inst & ~0x1f) == 0xd53bd040) // mrs x0, tpidr_el0
                {
                    uint rd = inst & 0x1f;
                    codePatch.AddCode(i, WriteMrsTpidrEl0Patch(rd), inst);
                    Logger.Debug?.Print(LogClass.Cpu, $"Patched MRS x{rd}, tpidr_el0 at 0x{address:X}.");
                }
                else if ((inst & ~0x1f) == 0xd53b0020 && OperatingSystem.IsMacOS()) // mrs x0, ctr_el0
                {
                    uint rd = inst & 0x1f;
                    codePatch.AddCode(i, WriteMrsCtrEl0Patch(rd), inst);
                    Logger.Debug?.Print(LogClass.Cpu, $"Patched MRS x{rd}, ctr_el0 at 0x{address:X}.");
                }
                else if ((inst & ~0x1f) == 0xd53be020) // mrs x0, cntpct_el0
                {
                    uint rd = inst & 0x1f;
                    codePatch.AddCode(i, WriteMrsCntpctEl0Patch(rd), inst);
                    Logger.Debug?.Print(LogClass.Cpu, $"Patched MRS x{rd}, cntpct_el0 at 0x{address:X}.");
                }
                else if ((inst & ~0x1f) == 0xd51bd040) // msr tpidr_el0, x0
                {
                    uint rd = inst & 0x1f;
                    codePatch.AddCode(i, WriteMsrTpidrEl0Patch(rd), inst);
                    Logger.Debug?.Print(LogClass.Cpu, $"Patched MSR tpidr_el0, x{rd} at 0x{address:X}.");
                }
                else if (((inst >> 23) & 0x7fu) == 0x10u && !IsEdenExclusive(inst))
                {
                    // A mascara abaixo tambem pega palavras que nao sao LDXR/STXR/LDXP/STXP (inclusive dados
                    // dentro do .text). O Eden (instructions.h, Exclusive::Verify) so aceita par (bit 21) ou
                    // Rt2 = 11111. Essas palavras nao sao alteradas (corrige o boot do Mario + Rabbids Sparks of Hope).
                }
                else if (((inst >> 23) & 0x7fu) == 0x10u) // LDXR/STXR/LDXP/STXP family (o2 = 0)
                {
                    // NEOV2 NCE-EXCL1: the Switch Cortex-A57 behaves as if every exclusive access were ordered,
                    // and some games depend on it. Newer cores do not, so an unordered exclusive can let a
                    // lock-free producer and consumer see each other's data out of order. Set the ordered
                    // bit (o0, bit 15) in place, turning LDXR/STXR into LDAXR/STLXR, as the LightningJit
                    // (InstEmitMemory, BaseRegister form) and the Eden NCE patcher (patcher.cpp) already do.
                    uint ordered = inst | (1u << 15);

                    if (ordered != inst)
                    {
                        codePatch.ReplaceInstruction(i, ordered);
                        orderedExclusives++;
                    }
                }
            }

            Logger.Info?.Print(LogClass.Cpu, $"[NEOV2-NCE-EXCL1] {orderedExclusives} exclusive accesses converted to ordered");

            return codePatch;
        }

        /// <summary>
        /// Mesmo filtro do Eden (src/core/arm/nce/instructions.h, Exclusive::Verify): dentro da familia de
        /// exclusivas (bits 29..23 = 0x10), so e instrucao valida se for de par (bit 21 = 1) ou se Rt2
        /// (bits 14..10) for 11111.
        /// </summary>
        private static bool IsEdenExclusive(uint inst)
        {
            bool pair = ((inst >> 21) & 1u) != 0;
            bool fixedRt2 = ((inst >> 10) & 0x1fu) == 0x1fu;

            return pair || fixedRt2;
        }

        private static uint[] WriteSvcPatch(uint svcId)
            => WriteSvcPatch(svcId, nint.Zero, nint.Zero);

        // Test pointers are selected at generation time; production retains its resolver and callbacks.
        private static uint[] WriteSvcPatch(uint svcId, nint nativeContextPtr, nint interruptHandlerPtr)
            => WriteSvcPatch(svcId, nativeContextPtr, interruptHandlerPtr, PlatformInfo.IsBionic);

        private static uint[] WriteSvcPatch(uint svcId, nint nativeContextPtr, nint interruptHandlerPtr, bool snapshot)
        {
            Assembler asm = new();

            WriteManagedCall(asm, (asm, ctx, tmp, tmp2) =>
            {
                for (int i = 0; i < 8; i++)
                {
                    asm.StrRiUn(Gpr(i), ctx, NceNativeContext.GetXOffset(i));
                }

                asm.Mov(Gpr(0, OperandType.I32), svcId);
                asm.LdrRiUn(tmp, ctx, NceNativeContext.GetSvcCallHandlerOffset());
                asm.Blr(tmp);

                Operand lblContinue = asm.CreateLabel();
                Operand lblQuit = asm.CreateLabel();

                asm.Cbnz(Gpr(0, OperandType.I32), lblContinue);

                asm.MarkLabel(lblQuit);

                CreateRegisterSaveRestoreForManaged().WriteEpilogue(asm);

                asm.Ret(Gpr(30));

                asm.MarkLabel(lblContinue);

                WriteManagedCallReturn(asm, ctx, tmp, tmp2, ThreadExitMethod.Label, lblQuit, interruptHandlerPtr);

                for (int i = 0; i < 8; i++)
                {
                    asm.LdrRiUn(Gpr(i), ctx, NceNativeContext.GetXOffset(i));
                }
            }, 0xff, nativeContextPtr, snapshot: snapshot, snapshotKind: 2);

            asm.B(0);

            return asm.GetCode();
        }

        private static uint[] WriteMrsTpidrroEl0Patch(uint rd)
        {
            return WriteMrsContextRead(rd, NceNativeContext.GetTpidrroEl0Offset());
        }

        private static uint[] WriteMrsTpidrEl0Patch(uint rd)
        {
            return WriteMrsContextRead(rd, NceNativeContext.GetTpidrEl0Offset());
        }

        private static uint[] WriteMrsCtrEl0Patch(uint rd)
        {
            return WriteMrsContextRead(rd, NceNativeContext.GetCtrEl0Offset());
        }

        private static uint[] WriteMrsCntpctEl0Patch(uint rd)
            => WriteMrsCntpctEl0Patch(rd, nint.Zero, nint.Zero, nint.Zero);

        private static uint[] WriteMrsCntpctEl0Patch(uint rd, nint nativeContextPtr, nint counterHandlerPtr, nint interruptHandlerPtr)
            => WriteMrsCntpctEl0Patch(rd, nativeContextPtr, counterHandlerPtr, interruptHandlerPtr, PlatformInfo.IsBionic);

        private static uint[] WriteMrsCntpctEl0Patch(uint rd, nint nativeContextPtr, nint counterHandlerPtr, nint interruptHandlerPtr, bool snapshot)
        {
            Assembler asm = new();

            WriteManagedCall(asm, (asm, ctx, tmp, tmp2) =>
            {
                asm.Mov(tmp, (ulong)(counterHandlerPtr == nint.Zero ? NceNativeInterface.GetTickCounterAccessFunctionPointer() : counterHandlerPtr));
                asm.Blr(tmp);
                asm.StrRiUn(Gpr(0), ctx, NceNativeContext.GetTempStorageOffset());

                WriteManagedCallReturn(asm, ctx, tmp, tmp2, ThreadExitMethod.GenerateReturn, interruptHandlerPtr: interruptHandlerPtr);

                asm.LdrRiUn(Gpr((int)rd), ctx, NceNativeContext.GetTempStorageOffset());
            }, 1u << (int)rd, nativeContextPtr, snapshot: snapshot, snapshotKind: 3, snapshotRd: (int)rd);

            asm.B(0);

            return asm.GetCode();
        }

        private static uint[] WriteMsrTpidrEl0Patch(uint rd)
            => WriteMsrTpidrEl0Patch(rd, nint.Zero);

        private static uint[] WriteMsrTpidrEl0Patch(uint rd, nint nativeContextPtr)
        {
            Assembler asm = new();

            Span<int> scratchRegs = stackalloc int[4];
            PickScratchRegs(scratchRegs, 1u << (int)rd);

            RegisterSaveRestore rsr = new((1 << scratchRegs[0]) | (1 << scratchRegs[1]) | (1 << scratchRegs[2]) | (1 << scratchRegs[3]));

            rsr.WritePrologue(asm);

            WriteMrsNzcv(asm, Gpr(scratchRegs[3]));
            if (nativeContextPtr == nint.Zero)
                WriteLoadContext(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]), Gpr(scratchRegs[2]));
            else
                asm.Mov(Gpr(scratchRegs[0]), (ulong)nativeContextPtr);
            asm.StrRiUn(Gpr((int)rd), Gpr(scratchRegs[0]), NceNativeContext.GetTpidrEl0Offset());
            WriteMsrNzcv(asm, Gpr(scratchRegs[3]));

            rsr.WriteEpilogue(asm);

            asm.B(0);

            return asm.GetCode();
        }

        private static uint[] WriteMrsContextRead(uint rd, int contextOffset)
            => WriteMrsContextRead(rd, contextOffset, nint.Zero, PlatformInfo.IsBionic);

        private static uint[] WriteMrsContextRead(uint rd, int contextOffset, nint nativeContextPtr, bool snapshot)
        {
            Assembler asm = new();

            Span<int> scratchRegs = stackalloc int[4];
            PickScratchRegs(scratchRegs, 1u << (int)rd);

            RegisterSaveRestore rsr = new((1 << scratchRegs[0]) | (1 << scratchRegs[1]) | (1 << scratchRegs[2]) | (1 << scratchRegs[3]));

            rsr.WritePrologue(asm);

            WriteMrsNzcv(asm, Gpr(scratchRegs[3]));
            if (nativeContextPtr == nint.Zero)
                WriteLoadContext(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]), Gpr(scratchRegs[2]));
            else
                asm.Mov(Gpr(scratchRegs[0]), (ulong)nativeContextPtr);
            if (snapshot) asm.StrRiUn(Gpr((int)rd), Gpr(scratchRegs[0]), NceNativeContext.GetSnapshotRdOffset());
            asm.Add(Gpr((int)rd), Gpr(scratchRegs[0]), Const((ulong)contextOffset));
            WriteMsrNzcv(asm, Gpr(scratchRegs[3]));

            rsr.WriteEpilogue(asm);

            asm.LdrRiUn(Gpr((int)rd), Gpr((int)rd), 0);

            asm.B(0);

            return asm.GetCode();
        }

        private static void WriteTryLoadIndexedContext(Assembler asm, Operand lblFound, Operand tmp0, Operand tmp1, Operand tmp2)
        {
            asm.MrsTpidrEl0(tmp1);
            asm.Mov(tmp2, NceThreadContextIndex.HashMultiplier);
            asm.Mul(tmp2, tmp1, tmp2);
            asm.Lsr(tmp2, tmp2, Const((ulong)(64 - NceThreadContextIndex.BucketBits)));
            asm.Mov(tmp0, (ulong)NceThreadContextIndex.EntriesPointer);
            asm.Add(tmp0, tmp0, tmp2, ArmShiftType.Lsl, NceThreadContextIndex.BucketStrideShift);

            for (int way = 0; way < NceThreadContextIndex.Ways; way++)
            {
                asm.LdrRiPost(tmp2, tmp0, NceThreadContextIndex.EntrySize);
                asm.Cmp(tmp1, tmp2);
                asm.B(lblFound, ArmCondition.Eq);
            }
        }

        private static void WriteLoadContext(Assembler asm, Operand tmp0, Operand tmp1, Operand tmp2)
        {
            Operand lblFastFound = default;
            Operand lblDone = default;

            if (UsesThreadContextIndex)
            {
                lblFastFound = asm.CreateLabel();
                lblDone = asm.CreateLabel();
                WriteTryLoadIndexedContext(asm, lblFastFound, tmp0, tmp1, tmp2);
            }

            asm.Mov(tmp0, (ulong)NceThreadTable.EntriesPointer);

            if (OperatingSystem.IsMacOS())
            {
                asm.MrsTpidrroEl0(tmp1);
            }
            else
            {
                asm.MrsTpidrEl0(tmp1);
            }

            Operand lblFound = asm.CreateLabel();
            Operand lblLoop = asm.CreateLabel();

            asm.MarkLabel(lblLoop);

            asm.LdrRiPost(tmp2, tmp0, 16);
            asm.Cmp(tmp1, tmp2);
            asm.B(lblFound, ArmCondition.Eq);
            asm.B(lblLoop);

            asm.MarkLabel(lblFound);

            asm.Ldur(tmp0, tmp0, -8);

            if (UsesThreadContextIndex)
            {
                asm.B(lblDone);
                asm.MarkLabel(lblFastFound);
                asm.Ldur(tmp0, tmp0, -8);
                asm.MarkLabel(lblDone);
            }
        }

        private static void WriteLoadContextSafe(Assembler asm, Operand lblFail, Operand tmp0, Operand tmp1, Operand tmp2, Operand tmp3)
        {
            Operand lblFastFound = default;
            Operand lblDone = default;

            if (UsesThreadContextIndex)
            {
                lblFastFound = asm.CreateLabel();
                lblDone = asm.CreateLabel();
                WriteTryLoadIndexedContext(asm, lblFastFound, tmp0, tmp1, tmp2);
            }

            asm.Mov(tmp0, (ulong)NceThreadTable.EntriesPointer);
            asm.Ldur(tmp3, tmp0, -8);
            asm.Add(tmp3, tmp0, tmp3, ArmShiftType.Lsl, 4);

            if (OperatingSystem.IsMacOS())
            {
                asm.MrsTpidrroEl0(tmp1);
            }
            else
            {
                asm.MrsTpidrEl0(tmp1);
            }

            Operand lblFound = asm.CreateLabel();
            Operand lblLoop = asm.CreateLabel();

            asm.MarkLabel(lblLoop);

            asm.Cmp(tmp0, tmp3);
            asm.B(lblFail, ArmCondition.GeUn);
            asm.LdrRiPost(tmp2, tmp0, 16);
            asm.Cmp(tmp1, tmp2);
            asm.B(lblFound, ArmCondition.Eq);
            asm.B(lblLoop);

            asm.MarkLabel(lblFound);

            asm.Ldur(tmp0, tmp0, -8);

            if (UsesThreadContextIndex)
            {
                asm.B(lblDone);
                asm.MarkLabel(lblFastFound);
                asm.Ldur(tmp0, tmp0, -8);
                asm.MarkLabel(lblDone);
            }
        }

        private static void PickScratchRegs(Span<int> scratchRegs, uint blacklistedRegMask)
        {
            int scratchReg = ScratchBaseReg;

            for (int i = 0; i < scratchRegs.Length; i++)
            {
                while ((blacklistedRegMask & (1u << scratchReg)) != 0)
                {
                    scratchReg++;
                }

                if (scratchReg >= 29)
                {
                    throw new ArgumentException($"No enough register for {scratchRegs.Length} scratch register, started from {ScratchBaseReg}");
                }

                scratchRegs[i] = scratchReg++;
            }
        }

        private static Operand Gpr(int register, OperandType type = OperandType.I64)
        {
            return new Operand(register, RegisterType.Integer, type);
        }

        private static Operand Vec(int register, OperandType type = OperandType.V128)
        {
            return new Operand(register, RegisterType.Vector, type);
        }

        private static Operand Const(ulong value)
        {
            return new Operand(OperandType.I64, value);
        }

        private static Operand Const(OperandType type, ulong value)
        {
            return new Operand(type, value);
        }

        private static void WriteMrsNzcv(Assembler asm, Operand rt) => asm.Mrs(rt, 1, 3, 4, 2, 0);
        private static void WriteMsrNzcv(Assembler asm, Operand rt) => asm.WriteInstruction(0xd51b4200u, rt);
        private static void WriteMrsFpcr(Assembler asm, Operand rt) => asm.Mrs(rt, 1, 3, 4, 4, 0);
        private static void WriteMsrFpcr(Assembler asm, Operand rt) => asm.WriteInstruction(0xd51b4400u, rt);
        private static void WriteMrsFpsr(Assembler asm, Operand rt) => asm.Mrs(rt, 1, 3, 4, 4, 1);
        private static void WriteMsrFpsr(Assembler asm, Operand rt) => asm.WriteInstruction(0xd51b4420u, rt);

        private static void WriteStoreGuestFpStateToContext(Assembler asm, Operand ctx, Operand tmp)
        {
            Operand tmp32 = Gpr(tmp.GetRegister().Index, OperandType.I32);
            WriteMrsFpcr(asm, tmp);
            asm.StrRiUn(tmp32, ctx, NceNativeContext.GetFpcrOffset());
            WriteMrsFpsr(asm, tmp);
            asm.StrRiUn(tmp32, ctx, NceNativeContext.GetFpsrOffset());
        }

        private static void WriteLoadGuestFpStateFromContext(Assembler asm, Operand ctx, Operand tmp)
        {
            Operand tmp32 = Gpr(tmp.GetRegister().Index, OperandType.I32);
            asm.LdrRiUn(tmp32, ctx, NceNativeContext.GetFpcrOffset());
            WriteMsrFpcr(asm, tmp);
            asm.LdrRiUn(tmp32, ctx, NceNativeContext.GetFpsrOffset());
            WriteMsrFpsr(asm, tmp);
        }

        private static void WriteStoreHostFpStateToContext(Assembler asm, Operand ctx, Operand tmp)
        {
            Operand tmp32 = Gpr(tmp.GetRegister().Index, OperandType.I32);
            WriteMrsFpcr(asm, tmp);
            asm.StrRiUn(tmp32, ctx, NceNativeContext.GetHostFpcrOffset());
            WriteMrsFpsr(asm, tmp);
            asm.StrRiUn(tmp32, ctx, NceNativeContext.GetHostFpsrOffset());
        }

        private static void WriteLoadHostFpStateFromContext(Assembler asm, Operand ctx, Operand tmp)
        {
            Operand tmp32 = Gpr(tmp.GetRegister().Index, OperandType.I32);
            asm.LdrRiUn(tmp32, ctx, NceNativeContext.GetHostFpcrOffset());
            WriteMsrFpcr(asm, tmp);
            asm.LdrRiUn(tmp32, ctx, NceNativeContext.GetHostFpsrOffset());
            WriteMsrFpsr(asm, tmp);
        }

        private static void WriteStoreNzcvToContext(Assembler asm, Operand ctx, Operand tmp, Operand nzcv)
        {
            Operand tmp32 = Gpr(tmp.GetRegister().Index, OperandType.I32);
            Operand nzcv32 = Gpr(nzcv.GetRegister().Index, OperandType.I32);
            asm.LdrRiUn(tmp32, ctx, NceNativeContext.GetPstateOffset());
            asm.And(tmp32, tmp32, Const(OperandType.I32, ~PstateNzcvMask & uint.MaxValue));
            asm.And(nzcv32, nzcv32, Const(OperandType.I32, PstateNzcvMask));
            asm.Orr(tmp32, tmp32, nzcv32);
            asm.StrRiUn(tmp32, ctx, NceNativeContext.GetPstateOffset());
        }

        private static void WriteLoadNzcvFromContext(Assembler asm, Operand ctx, Operand tmp)
        {
            Operand tmp32 = Gpr(tmp.GetRegister().Index, OperandType.I32);
            asm.LdrRiUn(tmp32, ctx, NceNativeContext.GetPstateOffset());
            WriteMsrNzcv(asm, tmp);
        }

        private static uint GetImm26(ulong sourceAddress, ulong targetAddress)
        {
            long offset = (long)(targetAddress - sourceAddress);
            long offsetTrunc = (offset >> 2) & 0x3FFFFFF;

            if ((offsetTrunc << 38) >> 36 != offset)
            {
                throw new Exception($"Offset out of range: 0x{sourceAddress:X} -> 0x{targetAddress:X} (0x{offset:X})");
            }

            return (uint)offsetTrunc;
        }

        private static int GetOffset(ulong sourceAddress, ulong targetAddress)
        {
            long offset = (long)(targetAddress - sourceAddress);

            return checked((int)offset);
        }

        private static uint[] GetCopy(uint[] code)
        {
            uint[] codeCopy = new uint[code.Length];
            code.CopyTo(codeCopy, 0);

            return codeCopy;
        }

        private static void WriteManagedCall(Assembler asm, Action<Assembler, Operand, Operand, Operand> writeCall, uint blacklistedRegMask, nint nativeContextPtr = default,
            bool snapshot = false, int snapshotKind = 0, int snapshotRd = -1)
        {
            int intMask = 0x7fffffff & (int)~blacklistedRegMask;
            int vecMask = unchecked((int)0xffffffff);

            Span<int> scratchRegs = stackalloc int[4];
            PickScratchRegs(scratchRegs, blacklistedRegMask);

            RegisterSaveRestore rsr = new(intMask, vecMask, OperandType.V128);

            rsr.WritePrologue(asm);

            WriteMrsNzcv(asm, Gpr(scratchRegs[3]));
            if (nativeContextPtr == nint.Zero)
            {
                WriteLoadContext(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]), Gpr(scratchRegs[2]));
            }
            else
            {
                asm.Mov(Gpr(scratchRegs[0]), (ulong)nativeContextPtr);
            }

            // Acquire on the guest stack before publishing snapshots or changing FP/HostSp.
            // scratchRegs[3] retains the guest NZCV; the existing helper does not change flags.
            WriteInManagedLockAcquire(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]), Gpr(scratchRegs[2]));
            WriteStoreNzcvToContext(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]), Gpr(scratchRegs[3]));

            WriteStoreGuestFpStateToContext(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]));
            WriteLoadHostFpStateFromContext(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]));

            asm.MovSp(Gpr(scratchRegs[1]), Gpr(Assembler.SpRegister));
            asm.StrRiUn(Gpr(scratchRegs[1]), Gpr(scratchRegs[0]), NceNativeContext.GetGuestSPOffset());
            if (snapshot)
            {
                asm.StrRiUn(Gpr(scratchRegs[1]), Gpr(scratchRegs[0]), NceNativeContext.GetSnapshotSourceOffset());
                asm.WriteInstruction(0x10000000u, Gpr(scratchRegs[2])); // ADR tmp2, . ; metadata identifies this trampoline.
                asm.StrRiUn(Gpr(scratchRegs[2]), Gpr(scratchRegs[0]), NceNativeContext.GetSnapshotPcOffset());
                if (snapshotRd >= 0) asm.StrRiUn(Gpr(snapshotRd), Gpr(scratchRegs[0]), NceNativeContext.GetSnapshotRdOffset());
                asm.Mov(Gpr(scratchRegs[1], OperandType.I32), Const(OperandType.I32, (ulong)snapshotKind));
                asm.StrRiUn(Gpr(scratchRegs[1], OperandType.I32), Gpr(scratchRegs[0]), NceNativeContext.GetSnapshotKindOffset());
            }
            asm.LdrRiUn(Gpr(scratchRegs[1]), Gpr(scratchRegs[0]), NceNativeContext.GetHostSPOffset());
            asm.MovSp(Gpr(Assembler.SpRegister), Gpr(scratchRegs[1]));

            writeCall(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]), Gpr(scratchRegs[2]));

            rsr.WriteEpilogue(asm);
        }

        internal static uint[] GenerateThreadStartCode()
        {
            return GenerateThreadStartCode(NceNativeInterface.GetSuspendThreadHandlerFunctionPointer(), PlatformInfo.IsBionic);
        }

        // The overload lets host tests execute the actual entry code in Unicorn with a synthetic callback.
        internal static uint[] GenerateThreadStartCode(nint interruptHandlerPtr)
            => GenerateThreadStartCode(interruptHandlerPtr, false);

        internal static uint[] GenerateThreadStartCode(nint interruptHandlerPtr, bool androidSignalExit)
        {
            Assembler asm = new();

            CreateRegisterSaveRestoreForManaged().WritePrologue(asm);

            WriteStoreHostFpStateToContext(asm, Gpr(0), Gpr(1));
            if (androidSignalExit)
            {
                // Capture before any guest register handoff. Android X18 must be host-owned
                // when sigreturn enters the native exit epilogue or a delayed signal arrives.
                asm.StrRiUn(Gpr(18), Gpr(0), NceNativeContext.GetHostX18Offset());
                asm.Mov(Gpr(1, OperandType.I32), Const(OperandType.I32, 4));
                asm.StrRiUn(Gpr(1, OperandType.I32), Gpr(0), NceNativeContext.GetSnapshotKindOffset());
            }

            asm.MovSp(Gpr(1), Gpr(Assembler.SpRegister));
            asm.StrRiUn(Gpr(1), Gpr(0), NceNativeContext.GetHostSPOffset());

            Operand lblLoadGuest = asm.CreateLabel();
            Operand lblRelease = asm.CreateLabel();
            Operand lblPending = asm.CreateLabel();
            Operand lblContinue = asm.CreateLabel();

            asm.MarkLabel(lblLoadGuest);

            for (int i = 2; i < 30; i += 2)
            {
                if (i == 18)
                {
                    // X18 is a platform register on Android. Keep its host value until handoff,
                    // so an initial interrupt callback never inherits the guest X18.
                    asm.LdrRiUn(Gpr(19), Gpr(0), NceNativeContext.GetXOffset(19));
                }
                else
                {
                    asm.LdpRiUn(Gpr(i), Gpr(i + 1), Gpr(0), NceNativeContext.GetXOffset(i));
                }
            }

            for (int i = 0; i < 32; i += 2)
            {
                asm.LdpRiUn(Vec(i), Vec(i + 1), Gpr(0), NceNativeContext.GetVOffset(i));
            }

            WriteLoadGuestFpStateFromContext(asm, Gpr(0), Gpr(1));

            asm.LdrRiUn(Gpr(1), Gpr(0), NceNativeContext.GetGuestSPOffset());
            asm.MovSp(Gpr(Assembler.SpRegister), Gpr(1));

            // Release only at the existing late handoff, after SP is on the guest stack.
            // A concurrent OR invalidates the reservation; retry instead of erasing its bit.
            asm.MarkLabel(lblRelease);
            asm.Add(Gpr(1), Gpr(0), Const((ulong)NceNativeContext.GetInManagedOffset()));
            asm.Ldaxr(Gpr(30, OperandType.I32), Gpr(1));
            asm.Cmp(Gpr(30, OperandType.I32), Const(OperandType.I32, 3));
            asm.B(lblPending, ArmCondition.Eq);
            asm.Cmp(Gpr(30, OperandType.I32), Const(OperandType.I32, 1));
            asm.B(lblRelease, ArmCondition.Ne); // Only 1 -> 0 is a valid initial release.
            // Restore NZCV before publishing native state: a signal may capture it immediately after STLXR.
            WriteLoadNzcvFromContext(asm, Gpr(0), Gpr(30));
            asm.Stlxr(Gpr(Assembler.ZrRegister, OperandType.I32), Gpr(1), Gpr(2, OperandType.I32));
            asm.Cbnz(Gpr(2, OperandType.I32), lblRelease);

            // The handshake uses X2/X30; finish restoring registers without changing NZCV.
            asm.LdrRiUn(Gpr(2), Gpr(0), NceNativeContext.GetXOffset(2));
            asm.LdrRiUn(Gpr(18), Gpr(0), NceNativeContext.GetXOffset(18));
            asm.LdrRiUn(Gpr(30), Gpr(0), NceNativeContext.GetXOffset(30));
            asm.LdpRiUn(Gpr(0), Gpr(1), Gpr(0), NceNativeContext.GetXOffset(0));
            asm.Br(Gpr(30));

            asm.MarkLabel(lblPending);

            // Consume 3 -> 1 atomically, keeping the managed bit set throughout the callback.
            asm.Mov(Gpr(30, OperandType.I32), Const(OperandType.I32, 1));
            asm.Stlxr(Gpr(30, OperandType.I32), Gpr(1), Gpr(2, OperandType.I32));
            asm.Cbnz(Gpr(2, OperandType.I32), lblRelease);

            asm.LdrRiUn(Gpr(1), Gpr(0), NceNativeContext.GetHostSPOffset());
            asm.MovSp(Gpr(Assembler.SpRegister), Gpr(1));
            asm.MovSp(Gpr(29), Gpr(Assembler.SpRegister)); // Re-establish the host frame pointer.
            WriteLoadHostFpStateFromContext(asm, Gpr(0), Gpr(1));
            asm.Mov(Gpr(19), Gpr(0)); // Preserve the context across the ABI callback.
            asm.Mov(Gpr(20), (ulong)interruptHandlerPtr);
            asm.Blr(Gpr(20));
            asm.Cbnz(Gpr(0, OperandType.I32), lblContinue);

            // Stop before entering guest code, even when the entry is a loop with no SVC.
            WriteLoadHostFpStateFromContext(asm, Gpr(19), Gpr(20));
            CreateRegisterSaveRestoreForManaged().WriteEpilogue(asm);
            asm.Ret(Gpr(30));

            asm.MarkLabel(lblContinue);
            asm.Mov(Gpr(0), Gpr(19));
            asm.B(lblLoadGuest); // Reload all guest state, and service another request if one arrived.

            return asm.GetCode();
        }

        internal static uint[] GenerateSuspendExceptionHandler()
        {
            return PlatformInfo.IsBionic
                ? GenerateAndroidSuspendExceptionHandler(nint.Zero, NceNativeInterface.GetSuspendThreadHandlerFunctionPointer())
                : GenerateSuspendExceptionHandler(nint.Zero, NceNativeInterface.GetSuspendThreadHandlerFunctionPointer());
        }

        // Nonzero context is an internal test seam; production retains the indexed/table lookup.
        internal static uint[] GenerateSuspendExceptionHandler(nint nativeContextPtr, nint interruptHandlerPtr)
        {
            Assembler asm = new();

            Span<int> scratchRegs = stackalloc int[4];
            PickScratchRegs(scratchRegs, 0u);

            RegisterSaveRestore rsr = new((1 << scratchRegs[0]) | (1 << scratchRegs[1]) | (1 << scratchRegs[2]) | (1 << scratchRegs[3]), hasCall: true);

            rsr.WritePrologue(asm);

            Operand lblAgain = asm.CreateLabel();
            Operand lblFail = asm.CreateLabel();

            if (nativeContextPtr == nint.Zero)
            {
                WriteLoadContextSafe(asm, lblFail, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]), Gpr(scratchRegs[2]), Gpr(scratchRegs[3]));
            }
            else
            {
                asm.Mov(Gpr(scratchRegs[0]), (ulong)nativeContextPtr);
            }

            asm.Cmp(Gpr(0, OperandType.I32), Const((ulong)NceThreadPal.UnixSuspendSignal));
            asm.B(lblFail, ArmCondition.Ne);

            // A delayed suspend signal may arrive after ownership has moved to managed code.
            // Preserve the pending bit and return on this signal stack; never reuse the active HostSp.
            Operand lblCheckMode = asm.CreateLabel();
            Operand lblNative = asm.CreateLabel();
            Operand stateAddress = Gpr(scratchRegs[1]);
            Operand state = Gpr(scratchRegs[2], OperandType.I32);
            Operand status = Gpr(scratchRegs[3], OperandType.I32);
            asm.Add(stateAddress, Gpr(scratchRegs[0]), Const((ulong)NceNativeContext.GetInManagedOffset()));
            asm.MarkLabel(lblCheckMode);
            asm.Ldaxr(state, stateAddress);
            asm.And(status, state, Const(OperandType.I32, 1));
            asm.Cbz(status, lblNative); // State 2 still belongs to guest execution.
            asm.Orr(state, state, Const(OperandType.I32, 2));
            // Keep address/value/status distinct for the exclusive store.
            asm.Stlxr(state, stateAddress, status);
            asm.Cbnz(status, lblCheckMode);
            asm.B(lblFail); // The existing release path services state 3 after the host call returns.
            asm.MarkLabel(lblNative);
            asm.Clrex(); // Do not carry our probe's reservation into the existing native path.

            if (OperatingSystem.IsLinux() || PlatformInfo.IsBionic)
            {
                asm.LdrRiUn(Gpr(scratchRegs[1]), Gpr(2), LinuxUContextPstateOffset);
                asm.StrRiUn(Gpr(scratchRegs[1], OperandType.I32), Gpr(scratchRegs[0]), NceNativeContext.GetPstateOffset());
            }

            WriteStoreGuestFpStateToContext(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]));
            WriteLoadHostFpStateFromContext(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]));

            asm.LdrRiUn(Gpr(scratchRegs[1]), Gpr(scratchRegs[0]), NceNativeContext.GetHostSPOffset());
            asm.MovSp(Gpr(scratchRegs[2]), Gpr(Assembler.SpRegister));
            asm.MovSp(Gpr(Assembler.SpRegister), Gpr(scratchRegs[1]));

            asm.Mov(Gpr(scratchRegs[1], OperandType.I32), Const(OperandType.I32, 1));
            asm.StrRiUn(Gpr(scratchRegs[1], OperandType.I32), Gpr(scratchRegs[0]), NceNativeContext.GetInManagedOffset());

            asm.MarkLabel(lblAgain);

            asm.Mov(Gpr(scratchRegs[3]), (ulong)interruptHandlerPtr);
            asm.Blr(Gpr(scratchRegs[3]));

            // TODO: Check return value, exit if we must.

            WriteInManagedLockReleaseForSuspendHandler(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]), Gpr(scratchRegs[3]), lblAgain);

            WriteLoadGuestFpStateFromContext(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]));

            asm.MovSp(Gpr(Assembler.SpRegister), Gpr(scratchRegs[2]));

            rsr.WriteEpilogue(asm);

            asm.Ret(Gpr(30));

            asm.MarkLabel(lblFail);

            rsr.WriteEpilogue(asm);

            asm.Ret(Gpr(30));

            return asm.GetCode();
        }

        internal static uint[] GenerateAndroidSuspendExceptionHandler(nint nativeContextPtr, nint interruptHandlerPtr)
        {
            Assembler asm = new();

            Span<int> scratchRegs = stackalloc int[4];
            PickScratchRegs(scratchRegs, 0u);

            RegisterSaveRestore rsr = new((1 << scratchRegs[0]) | (1 << scratchRegs[1]) | (1 << scratchRegs[2]) | (1 << scratchRegs[3]) | (1 << 23), hasCall: true);

            rsr.WritePrologue(asm);
            asm.Mov(Gpr(23), Gpr(2)); // Full kernel ucontext stays live across native/managed calls.

            Operand lblAgain = asm.CreateLabel();
            Operand lblFail = asm.CreateLabel();
            Operand hostInterrupt = asm.CreateLabel();
            Operand stopGuest = asm.CreateLabel(), stopHost = asm.CreateLabel();

            if (nativeContextPtr == nint.Zero)
            {
                WriteLoadContextSafe(asm, lblFail, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]), Gpr(scratchRegs[2]), Gpr(scratchRegs[3]));
            }
            else
            {
                asm.Mov(Gpr(scratchRegs[0]), (ulong)nativeContextPtr);
            }

            asm.Cmp(Gpr(0, OperandType.I32), Const((ulong)NceThreadPal.UnixSuspendSignal));
            asm.B(lblFail, ArmCondition.Ne);

            // A delayed suspend signal may arrive after ownership has moved to managed code.
            // Preserve the pending bit and return on this signal stack; never reuse the active HostSp.
            Operand lblCheckMode = asm.CreateLabel();
            Operand lblNative = asm.CreateLabel();
            Operand stateAddress = Gpr(scratchRegs[1]);
            Operand state = Gpr(scratchRegs[2], OperandType.I32);
            Operand status = Gpr(scratchRegs[3], OperandType.I32);
            asm.Add(stateAddress, Gpr(scratchRegs[0]), Const((ulong)NceNativeContext.GetInManagedOffset()));
            asm.MarkLabel(lblCheckMode);
            asm.Ldaxr(state, stateAddress);
            asm.And(status, state, Const(OperandType.I32, 1));
            asm.Cbz(status, lblNative); // State 2 still belongs to guest execution.
            asm.Orr(state, state, Const(OperandType.I32, 2));
            // Keep address/value/status distinct for the exclusive store.
            asm.Stlxr(state, stateAddress, status);
            asm.Cbnz(status, lblCheckMode);
            asm.B(lblFail); // The existing release path services state 3 after the host call returns.
            asm.MarkLabel(lblNative);
            asm.Clrex(); // Do not carry our probe's reservation into the existing native path.

            asm.Cbz(Gpr(2), lblFail);
            asm.LdrRiUn(Gpr(scratchRegs[1]), Gpr(2), LinuxUContextPcOffset);
            asm.LdrRiUn(Gpr(scratchRegs[2]), Gpr(scratchRegs[0]), NceNativeContext.GetGuestAddressStartOffset());
            asm.Cmp(Gpr(scratchRegs[1]), Gpr(scratchRegs[2]));
            asm.B(hostInterrupt, ArmCondition.LtUn);
            asm.LdrRiUn(Gpr(scratchRegs[2]), Gpr(scratchRegs[0]), NceNativeContext.GetGuestAddressEndOffset());
            asm.Cmp(Gpr(scratchRegs[1]), Gpr(scratchRegs[2]));
            asm.B(hostInterrupt, ArmCondition.GeUn);


            asm.LdrRiUn(Gpr(scratchRegs[1]), Gpr(2), LinuxUContextPstateOffset);
            asm.StrRiUn(Gpr(scratchRegs[1], OperandType.I32), Gpr(scratchRegs[0]), NceNativeContext.GetPstateOffset());
            WriteSnapshotSignalSource(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]), Gpr(23));

            WriteStoreGuestFpStateToContext(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]));
            WriteLoadHostFpStateFromContext(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]));

            asm.LdrRiUn(Gpr(scratchRegs[1]), Gpr(scratchRegs[0]), NceNativeContext.GetHostSPOffset());
            asm.MovSp(Gpr(scratchRegs[2]), Gpr(Assembler.SpRegister));
            asm.MovSp(Gpr(Assembler.SpRegister), Gpr(scratchRegs[1]));

            asm.Mov(Gpr(scratchRegs[1], OperandType.I32), Const(OperandType.I32, 1));
            asm.StrRiUn(Gpr(scratchRegs[1], OperandType.I32), Gpr(scratchRegs[0]), NceNativeContext.GetInManagedOffset());

            asm.MarkLabel(lblAgain);

            asm.Mov(Gpr(scratchRegs[3]), (ulong)interruptHandlerPtr);
            asm.Blr(Gpr(scratchRegs[3]));

            asm.Cbz(Gpr(0, OperandType.I32), stopGuest);

            WriteAndroidSignalReturn(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]),
                Gpr(scratchRegs[3]), Gpr(scratchRegs[2]), interruptHandlerPtr, stopGuest);

            rsr.WriteEpilogue(asm);

            asm.Ret(Gpr(30));


            asm.MarkLabel(stopGuest);
            WriteAndroidSignalStop(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]), Gpr(scratchRegs[3]),
                Gpr(scratchRegs[2]), Gpr(23));
            rsr.WriteEpilogue(asm);
            asm.Ret(Gpr(30));

            asm.MarkLabel(hostInterrupt);
            WriteSnapshotSignalSource(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]), Gpr(23));
            // Initial-entry/helper tails or a previous handler's host continuation may have
            // native state but no guest PC. Service on this signal frame, without reusing
            // HostSp or replacing the guest snapshot with host SP/FP/NZCV.
            asm.Sub(Gpr(Assembler.SpRegister), Gpr(Assembler.SpRegister), Const(16UL));
            WriteMrsFpcr(asm, Gpr(scratchRegs[1]));
            asm.StrRiUn(Gpr(scratchRegs[1]), Gpr(Assembler.SpRegister), 0);
            WriteMrsFpsr(asm, Gpr(scratchRegs[1]));
            asm.StrRiUn(Gpr(scratchRegs[1]), Gpr(Assembler.SpRegister), 8);
            asm.MovSp(Gpr(scratchRegs[2]), Gpr(Assembler.SpRegister));
            WriteLoadHostFpStateFromContext(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]));
            asm.Mov(Gpr(scratchRegs[1], OperandType.I32), Const(OperandType.I32, 1));
            asm.StrRiUn(Gpr(scratchRegs[1], OperandType.I32), Gpr(scratchRegs[0]), NceNativeContext.GetInManagedOffset());
            // This callback consumes the delivered native request; later ORs become state 3.
            asm.Mov(Gpr(scratchRegs[3]), (ulong)interruptHandlerPtr);
            asm.Blr(Gpr(scratchRegs[3]));
            asm.Cbz(Gpr(0, OperandType.I32), stopHost);
            WriteAndroidSignalReturn(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]),
                Gpr(scratchRegs[3]), Gpr(scratchRegs[2]), interruptHandlerPtr, stopHost, restoreLocalFp: true);
            asm.Add(Gpr(Assembler.SpRegister), Gpr(Assembler.SpRegister), Const(16UL));
            rsr.WriteEpilogue(asm);
            asm.Ret(Gpr(30));

            asm.MarkLabel(stopHost);
            WriteAndroidSignalStop(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]), Gpr(scratchRegs[3]),
                Gpr(scratchRegs[2]), Gpr(23), restoreLocalFp: true);
            asm.Add(Gpr(Assembler.SpRegister), Gpr(Assembler.SpRegister), Const(16UL));
            rsr.WriteEpilogue(asm);
            asm.Ret(Gpr(30));

            asm.MarkLabel(lblFail);

            rsr.WriteEpilogue(asm);

            asm.Ret(Gpr(30));

            return asm.GetCode();
        }

        // Android production generator, also emitted directly by the host Unicorn fixtures.
        internal static uint[] GenerateAndroidWrapperExceptionHandler(nint signalHandlerPtr,
            nint nativeContextPtr, nint interruptHandlerPtr, nint previousHandlerPtr)
        {
            if (previousHandlerPtr == nint.Zero || previousHandlerPtr == (nint)1)
            {
                throw new ArgumentException("Android faults need the native prior-action chain, not a disposition sentinel.", nameof(previousHandlerPtr));
            }
            Assembler asm = new();
            RegisterSaveRestore rsr = new((1 << 19) | (1 << 20) | (1 << 21) | (1 << 22) | (1 << 23), hasCall: true);
            rsr.WritePrologue(asm);
            asm.Mov(Gpr(23), Gpr(2));
            Operand host = asm.CreateLabel();
            Operand lookupFail = asm.CreateLabel();
            Operand claim = asm.CreateLabel();
            Operand stopGuest = asm.CreateLabel();
            Operand ctx = Gpr(19), tmp = Gpr(20), signalSp = Gpr(21), tmp2 = Gpr(22);
            Operand value = Gpr(21, OperandType.I32), status = Gpr(22, OperandType.I32);
            if (nativeContextPtr == nint.Zero)
                WriteLoadContextSafe(asm, lookupFail, ctx, tmp, signalSp, tmp2);
            else
                asm.Mov(ctx, (ulong)nativeContextPtr);

            asm.MarkLabel(claim);
            asm.Add(tmp, ctx, Const((ulong)NceNativeContext.GetInManagedOffset()));
            asm.Ldaxr(value, tmp);
            asm.And(status, value, Const(OperandType.I32, 1));
            asm.Cbnz(status, host); // Managed/transition: no snapshot and no reuse of HostSp.
            asm.Cmp(value, Const(OperandType.I32, 3));
            asm.B(host, ArmCondition.GtUn);
            asm.Cbz(Gpr(2), host);
            asm.Cbz(Gpr(1), host);
            Operand memoryFault = asm.CreateLabel();
            asm.LdrRiUn(status, Gpr(1), 8); // si_code, before treating the event as a guest memory fault.
            asm.Cmp(status, Const(OperandType.I32, 1));
            asm.B(memoryFault, ArmCondition.Eq);
            asm.Cmp(status, Const(OperandType.I32, 2));
            asm.B(host, ArmCondition.Ne);
            asm.MarkLabel(memoryFault);
            // PC provenance also distinguishes helper/host faults while the state is still native.
            asm.LdrRiUn(signalSp, Gpr(2), LinuxUContextPcOffset);
            asm.LdrRiUn(tmp2, ctx, NceNativeContext.GetGuestAddressStartOffset());
            asm.Cmp(signalSp, tmp2);
            asm.B(host, ArmCondition.LtUn);
            asm.LdrRiUn(tmp2, ctx, NceNativeContext.GetGuestAddressEndOffset());
            asm.Cmp(signalSp, tmp2);
            asm.B(host, ArmCondition.GeUn);
            // PC used the value register. Reload the state and test it again under the reservation.
            asm.Ldaxr(value, tmp);
            asm.And(status, value, Const(OperandType.I32, 1));
            asm.Cbnz(status, host);
            asm.Orr(value, value, Const(OperandType.I32, 1)); // 0->1, 2->3: preserve pending, never wait for a masked signal.
            asm.Stlxr(value, tmp, status);
            asm.Cbnz(status, claim);

            WriteSnapshotSignalSource(asm, ctx, tmp, Gpr(23));
            WriteFaultExecutionStateSnapshot(asm, ctx, Gpr(2), tmp,
                LinuxUContextSpOffset, LinuxUContextPcOffset, LinuxUContextPstateOffset);
            WriteStoreGuestFpStateToContext(asm, ctx, tmp);
            WriteLoadHostFpStateFromContext(asm, ctx, tmp);
            asm.MovSp(signalSp, Gpr(Assembler.SpRegister));
            asm.LdrRiUn(tmp, ctx, NceNativeContext.GetHostSPOffset());
            asm.MovSp(Gpr(Assembler.SpRegister), tmp);
            asm.Mov(tmp2, (ulong)signalHandlerPtr);
            asm.Blr(tmp2);
            WriteAndroidSignalReturn(asm, ctx, tmp, tmp2, signalSp, interruptHandlerPtr, stopGuest);
            rsr.WriteEpilogue(asm);
            asm.Ret(Gpr(30));

            asm.MarkLabel(stopGuest);
            WriteAndroidSignalStop(asm, ctx, tmp, tmp2, signalSp, Gpr(23));
            rsr.WriteEpilogue(asm);
            asm.Ret(Gpr(30));

            asm.MarkLabel(host);
            asm.MarkLabel(lookupFail);
            asm.Clrex();
            // R10 fix: neither NCE ownership nor an alternate signal stack establishes a
            // NativeAOT reverse-P/Invoke boundary. A cooperative runtime fault (including
            // its implicit null checks on non-NCE worker threads) must reach the native
            // prior handler without entering managed tracking or an interrupt callback.
            // Restore the signal frame and preserve all original signal arguments.
            rsr.WriteEpilogue(asm);
            asm.Mov(Gpr(3), (ulong)previousHandlerPtr);
            asm.Br(Gpr(3));
            return asm.GetCode();
        }

        private static void WriteSnapshotSignalSource(Assembler asm, Operand ctx, Operand tmp, Operand frame)
        {
            asm.StrRiUn(frame, ctx, NceNativeContext.GetSnapshotSourceOffset());
            asm.Mov(Gpr(tmp.GetRegister().Index, OperandType.I32), Const(OperandType.I32, 1));
            asm.StrRiUn(Gpr(tmp.GetRegister().Index, OperandType.I32), ctx, NceNativeContext.GetSnapshotKindOffset());
        }

        private static void WriteAndroidSignalReturn(Assembler asm, Operand ctx, Operand tmp,
            Operand tmp2, Operand signalSp, nint interruptHandlerPtr, Operand stop, bool restoreLocalFp = false)
        {
            Operand prepare = asm.CreateLabel(), retry = asm.CreateLabel();
            Operand pending = asm.CreateLabel(), done = asm.CreateLabel();
            Operand status = Gpr(tmp.GetRegister().Index, OperandType.I32);
            Operand value = Gpr(tmp2.GetRegister().Index, OperandType.I32);
            asm.MarkLabel(prepare);
            // SIGUSR2 is blocked by the SIGSEGV action mask, or by its own signal delivery.
            // Kernel sigreturn restores the original frame and mask together after this tail.

            asm.MovSp(Gpr(Assembler.SpRegister), signalSp);
            if (restoreLocalFp)
            {
                asm.LdrRiUn(tmp, Gpr(Assembler.SpRegister), 0);
                WriteMsrFpcr(asm, tmp);
                asm.LdrRiUn(tmp, Gpr(Assembler.SpRegister), 8);
                WriteMsrFpsr(asm, tmp);
            }
            else
            {
                WriteLoadGuestFpStateFromContext(asm, ctx, tmp);
            }
            asm.Add(ctx, ctx, Const((ulong)NceNativeContext.GetInManagedOffset()));
            asm.MarkLabel(retry);
            asm.Ldaxr(value, ctx);
            asm.Cmp(value, Const(OperandType.I32, 3));
            asm.B(pending, ArmCondition.Eq);
            asm.Stlxr(Gpr(Assembler.ZrRegister, OperandType.I32), ctx, status);
            asm.Cbnz(status, retry);
            asm.Sub(ctx, ctx, Const((ulong)NceNativeContext.GetInManagedOffset()));
            asm.B(done);
            asm.MarkLabel(pending);
            asm.Mov(value, Const(OperandType.I32, 1));
            asm.Stlxr(value, ctx, status);
            asm.Cbnz(status, retry);
            asm.Sub(ctx, ctx, Const((ulong)NceNativeContext.GetInManagedOffset()));
            WriteLoadHostFpStateFromContext(asm, ctx, tmp);

            if (!restoreLocalFp)
            {
                asm.LdrRiUn(tmp, ctx, NceNativeContext.GetHostSPOffset());
                asm.MovSp(Gpr(Assembler.SpRegister), tmp);
            }
            asm.Mov(tmp, (ulong)interruptHandlerPtr);
            asm.Blr(tmp);
            asm.Cbz(Gpr(0, OperandType.I32), stop); // False must never republish native ownership.
            asm.B(prepare);
            asm.MarkLabel(done);
        }

        // The kernel, not this handler, restores the interrupted mask/altstack/register frame.
        // Keep InManaged=1/3 until Execute finally withdraws the handle and unregisters context.
        private static void WriteAndroidSignalStop(Assembler asm, Operand ctx, Operand tmp,
            Operand tmp2, Operand signalSp, Operand signalContext, bool restoreLocalFp = false)
        {
            asm.Clrex();
            asm.LdrRiUn(tmp, ctx, NceNativeContext.GetHostSPOffset());
            asm.StrRiUn(tmp, signalContext, LinuxUContextSpOffset);
            asm.StrRiUn(ctx, signalContext, 184); // uc_mcontext.regs[0]: exit epilogue context.
            asm.LdrRiUn(tmp, ctx, NceNativeContext.GetHostX18Offset());
            asm.StrRiUn(tmp, signalContext, 184 + 18 * 8);
            asm.LdrRiUn(tmp2, ctx, NceNativeContext.GetSignalExitPcOffset());
            asm.StrRiUn(tmp2, signalContext, LinuxUContextPcOffset); // Publish the complete return target last.
            asm.MovSp(Gpr(Assembler.SpRegister), signalSp);
            if (restoreLocalFp)
            {
                asm.LdrRiUn(tmp, Gpr(Assembler.SpRegister), 0);
                WriteMsrFpcr(asm, tmp);
                asm.LdrRiUn(tmp, Gpr(Assembler.SpRegister), 8);
                WriteMsrFpsr(asm, tmp);
            }
            else
            {
                WriteLoadGuestFpStateFromContext(asm, ctx, tmp);
            }
        }

        internal static uint[] GenerateAndroidSignalExitCode()
        {
            Assembler asm = new();
            // Only entered by kernel sigreturn, with x0=context and SP at the original
            // threadStart host save area. No managed call, TLS lookup, signal-mask syscall
            // or RET from inside the signal handler. This is the existing host epilogue.
            asm.LdrRiUn(Gpr(18), Gpr(0), NceNativeContext.GetHostX18Offset());
            WriteLoadHostFpStateFromContext(asm, Gpr(0), Gpr(1));
            CreateRegisterSaveRestoreForManaged().WriteEpilogue(asm);
            asm.Ret(Gpr(30));
            return asm.GetCode();
        }

        internal static uint[] GenerateWrapperExceptionHandler(nint oldSignalHandlerSegfaultPtr, nint signalHandlerPtr)
            => GenerateWrapperExceptionHandler(oldSignalHandlerSegfaultPtr, signalHandlerPtr, nint.Zero, nint.Zero);

        // Synthetic resolver/callback selection at generation time only; production uses the two-argument entry.
        internal static uint[] GenerateWrapperExceptionHandler(nint oldSignalHandlerSegfaultPtr, nint signalHandlerPtr,
            nint nativeContextPtr, nint interruptHandlerPtr)
        {
            Assembler asm = new();

            Span<int> scratchRegs = stackalloc int[4];
            PickScratchRegs(scratchRegs, 0u);

            RegisterSaveRestore rsr = new((1 << scratchRegs[0]) | (1 << scratchRegs[1]) | (1 << scratchRegs[2]) | (1 << scratchRegs[3]), hasCall: true);

            rsr.WritePrologue(asm);

            Operand lblFail = asm.CreateLabel();

            if (nativeContextPtr == nint.Zero)
            {
                WriteLoadContextSafe(asm, lblFail, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]), Gpr(scratchRegs[2]), Gpr(scratchRegs[3]));
            }
            else
            {
                asm.Mov(Gpr(scratchRegs[0]), (ulong)nativeContextPtr);
            }

            // Claim while still on the signal stack and before changing guest state/FP.
            // Pending native suspension can then use HostSp without overlapping this wrapper.
            WriteInManagedLockAcquire(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]), Gpr(scratchRegs[3]));

            if (OperatingSystem.IsLinux() || PlatformInfo.IsBionic)
            {
                WriteFaultExecutionStateSnapshot(
                    asm,
                    Gpr(scratchRegs[0]),
                    Gpr(2),
                    Gpr(scratchRegs[1]),
                    LinuxUContextSpOffset,
                    LinuxUContextPcOffset,
                    LinuxUContextPstateOffset);
            }
            else if (OperatingSystem.IsMacOS() || OperatingSystem.IsIOS())
            {
                asm.LdrRiUn(Gpr(scratchRegs[2]), Gpr(2), DarwinUContextMContextOffset);
                WriteFaultExecutionStateSnapshot(
                    asm,
                    Gpr(scratchRegs[0]),
                    Gpr(scratchRegs[2]),
                    Gpr(scratchRegs[1]),
                    DarwinMContextSpOffset,
                    DarwinMContextPcOffset,
                    -1);
            }

            WriteStoreGuestFpStateToContext(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]));
            WriteLoadHostFpStateFromContext(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]));

            asm.LdrRiUn(Gpr(scratchRegs[1]), Gpr(scratchRegs[0]), NceNativeContext.GetHostSPOffset());
            asm.MovSp(Gpr(scratchRegs[2]), Gpr(Assembler.SpRegister));
            asm.MovSp(Gpr(Assembler.SpRegister), Gpr(scratchRegs[1]));

            asm.Mov(Gpr(scratchRegs[3]), (ulong)signalHandlerPtr);
            asm.Blr(Gpr(scratchRegs[3]));

            WriteInManagedLockRelease(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]), Gpr(scratchRegs[3]), ThreadExitMethod.None, interruptHandlerPtr: interruptHandlerPtr);

            WriteLoadGuestFpStateFromContext(asm, Gpr(scratchRegs[0]), Gpr(scratchRegs[1]));

            asm.MovSp(Gpr(Assembler.SpRegister), Gpr(scratchRegs[2]));

            rsr.WriteEpilogue(asm);

            asm.Ret(Gpr(30));

            asm.MarkLabel(lblFail);

            rsr.WriteEpilogue(asm);

            asm.Mov(Gpr(3), (ulong)oldSignalHandlerSegfaultPtr);
            asm.Br(Gpr(3));

            return asm.GetCode();
        }

        private static void WriteFaultExecutionStateSnapshot(
            Assembler asm,
            Operand ctx,
            Operand signalContext,
            Operand tmp,
            int spOffset,
            int pcOffset,
            int pstateOffset)
        {
            // NEOV2 NCE R3: tracked-memory faults are a normal hot path. Copying all 31
            // guest registers on every handled SIGSEGV added work that no normal tracking
            // callback consumes. Keep only the execution state used asynchronously.
            asm.LdrRiUn(tmp, signalContext, spOffset);
            asm.StrRiUn(tmp, ctx, NceNativeContext.GetGuestSPOffset());

            asm.LdrRiUn(tmp, signalContext, pcOffset);
            asm.StrRiUn(tmp, ctx, NceNativeContext.GetPcOffset());

            if (pstateOffset >= 0)
            {
                asm.LdrRiUn(tmp, signalContext, pstateOffset);
                asm.StrRiUn(Gpr(tmp.GetRegister().Index, OperandType.I32), ctx, NceNativeContext.GetPstateOffset());
            }
        }

        private static void WriteInManagedLockAcquire(Assembler asm, Operand ctx, Operand tmp, Operand tmp2)
        {
            Operand tmpUint = new(tmp.GetRegister().Index, RegisterType.Integer, OperandType.I32);
            Operand tmp2Uint = new(tmp2.GetRegister().Index, RegisterType.Integer, OperandType.I32);

            Operand lblLoop = asm.CreateLabel();

            // Bit 0 set means that the thread is currently executing managed code (that case should be impossible here).
            // Bit 1 being set means there is a signal pending, we should wait for the signal, otherwise it could trigger
            // while running managed code.

            // Use ctx temporarily as the state address. tmp is a separate status register;
            // STLXR must not use the same architectural register for its base and status.
            asm.Add(ctx, ctx, Const((ulong)NceNativeContext.GetInManagedOffset()));
            asm.MarkLabel(lblLoop);
            asm.Ldaxr(tmp2Uint, ctx);
            asm.Cbnz(tmp2Uint, lblLoop);
            asm.Mov(tmp2Uint, Const(OperandType.I32, 1));
            asm.Stlxr(tmp2Uint, ctx, tmpUint);
            asm.Cbnz(tmpUint, lblLoop); // Retry if store failed.
            asm.Sub(ctx, ctx, Const((ulong)NceNativeContext.GetInManagedOffset())); // Restore the context before any exit/callback.
        }

        private enum ThreadExitMethod
        {
            None,
            GenerateReturn,
            Label
        }

        private static void WriteInManagedLockRelease(Assembler asm, Operand ctx, Operand tmp, Operand tmp2, ThreadExitMethod exitMethod, Operand lblQuit = default, nint interruptHandlerPtr = default)
        {
            Operand tmpUint = new(tmp.GetRegister().Index, RegisterType.Integer, OperandType.I32);
            Operand tmp2Uint = new(tmp2.GetRegister().Index, RegisterType.Integer, OperandType.I32);

            Operand lblLoop = asm.CreateLabel();
            Operand lblInterrupt = asm.CreateLabel();
            Operand lblDone = asm.CreateLabel();

            // Bit 0 set means that the thread is currently executing managed code (it should be always set here, as we just returned from managed code).
            // Bit 1 being set means a interrupt was requested while it was in managed, we should service it.

            // Use ctx temporarily as the state address. tmp is a separate status register;
            // STLXR must not use the same architectural register for its base and status.
            asm.Add(ctx, ctx, Const((ulong)NceNativeContext.GetInManagedOffset()));
            asm.MarkLabel(lblLoop);
            asm.Ldaxr(tmp2Uint, ctx);
            asm.Cmp(tmp2Uint, Const(OperandType.I32, 3));
            asm.B(lblInterrupt, ArmCondition.Eq);
            asm.Stlxr(Gpr(Assembler.ZrRegister, OperandType.I32), ctx, tmpUint);
            asm.Cbnz(tmpUint, lblLoop); // Retry if store failed.
            asm.Sub(ctx, ctx, Const((ulong)NceNativeContext.GetInManagedOffset())); // Restore the context before any exit/callback.
            asm.B(lblDone);

            asm.MarkLabel(lblInterrupt);

            // If we got here, a interrupt was requested while it was in managed code.
            // Let's service the interrupt and check what we should do next.

            asm.Mov(tmp2Uint, Const(OperandType.I32, 1));
            asm.Stlxr(tmp2Uint, ctx, tmpUint);
            asm.Cbnz(tmpUint, lblLoop); // Retry if store failed.
            asm.Sub(ctx, ctx, Const((ulong)NceNativeContext.GetInManagedOffset())); // Restore the context before any exit/callback.
            asm.Mov(tmp, (ulong)(interruptHandlerPtr == nint.Zero ? NceNativeInterface.GetSuspendThreadHandlerFunctionPointer() : interruptHandlerPtr));
            asm.Blr(tmp);
            asm.Add(ctx, ctx, Const((ulong)NceNativeContext.GetInManagedOffset())); // The retry loop expects the state address.

            // The return value from the interrupt handler indicates if we should continue running.
            // From here, we either try to release the lock again. We might have received another interrupt
            // request in the meantime, in which case we should service it again.
            // If we were requested to exit, then we exit if we can.
            // TODO: We should also exit while on a signal handler. To do that we need to modify the PC value on the
            // context. It's a bit more tricky to do, so for now we ignore that case with "ThreadExitMethod.None".

            if (exitMethod == ThreadExitMethod.None)
            {
                asm.B(lblLoop);
            }
            else
            {
                asm.Cbnz(Gpr(0, OperandType.I32), lblLoop);
                asm.Sub(ctx, ctx, Const((ulong)NceNativeContext.GetInManagedOffset())); // False/stop also leaves a context base.

                if (exitMethod == ThreadExitMethod.Label)
                {
                    asm.B(lblQuit);
                }
                else if (exitMethod == ThreadExitMethod.GenerateReturn)
                {
                    CreateRegisterSaveRestoreForManaged().WriteEpilogue(asm);

                    asm.Ret(Gpr(30));
                }
            }

            asm.MarkLabel(lblDone);
        }

        // SVC/counter only. Signal wrappers retain their separate R6 release contract.
        private static void WriteManagedCallReturn(Assembler asm, Operand ctx, Operand tmp, Operand tmp2,
            ThreadExitMethod exitMethod, Operand lblQuit = default, nint interruptHandlerPtr = default)
        {
            if (exitMethod != ThreadExitMethod.Label && exitMethod != ThreadExitMethod.GenerateReturn)
            {
                throw new ArgumentException("Managed return requires a host exit policy.", nameof(exitMethod));
            }

            Operand status = Gpr(tmp.GetRegister().Index, OperandType.I32);
            Operand value = Gpr(tmp2.GetRegister().Index, OperandType.I32);
            Operand prepare = asm.CreateLabel();
            Operand retry = asm.CreateLabel();
            Operand pending = asm.CreateLabel();
            Operand done = asm.CreateLabel();

            asm.MarkLabel(prepare);
            // Keep managed ownership throughout FP/SP restoration. A pending request cannot reuse HostSp.
            WriteLoadGuestFpStateFromContext(asm, ctx, tmp);
            asm.LdrRiUn(tmp, ctx, NceNativeContext.GetGuestSPOffset());
            asm.MovSp(Gpr(Assembler.SpRegister), tmp);
            asm.Add(ctx, ctx, Const((ulong)NceNativeContext.GetInManagedOffset()));

            asm.MarkLabel(retry);
            asm.Ldaxr(value, ctx);
            asm.Cmp(value, Const(OperandType.I32, 3));
            asm.B(pending, ArmCondition.Eq);
            // ctx is temporarily the state address. Pstate is 20 bytes before it in this layout.
            // Reload NZCV after CMP and before publishing native state, including every failed-store retry.
            asm.Ldur(value, ctx, NceNativeContext.GetPstateOffset() - NceNativeContext.GetInManagedOffset());
            WriteMsrNzcv(asm, tmp2);
            asm.Stlxr(Gpr(Assembler.ZrRegister, OperandType.I32), ctx, status);
            asm.Cbnz(status, retry);
            asm.Sub(ctx, ctx, Const((ulong)NceNativeContext.GetInManagedOffset()));
            asm.B(done);

            asm.MarkLabel(pending);
            asm.Mov(value, Const(OperandType.I32, 1));
            asm.Stlxr(value, ctx, status);
            asm.Cbnz(status, retry);
            asm.Sub(ctx, ctx, Const((ulong)NceNativeContext.GetInManagedOffset()));
            // The HLE callback must run with host FP and stack, even if pending appeared on the guest tail.
            WriteLoadHostFpStateFromContext(asm, ctx, tmp);
            asm.LdrRiUn(tmp, ctx, NceNativeContext.GetHostSPOffset());
            asm.MovSp(Gpr(Assembler.SpRegister), tmp);
            asm.Mov(tmp, (ulong)(interruptHandlerPtr == nint.Zero ? NceNativeInterface.GetSuspendThreadHandlerFunctionPointer() : interruptHandlerPtr));
            asm.Blr(tmp);
            asm.Cbnz(Gpr(0, OperandType.I32), prepare);

            // Preserve false/stop through the original host frame, never from the guest tail.
            if (exitMethod == ThreadExitMethod.Label)
            {
                asm.B(lblQuit);
            }
            else
            {
                CreateRegisterSaveRestoreForManaged().WriteEpilogue(asm);
                asm.Ret(Gpr(30));
            }

            asm.MarkLabel(done);
        }

        private static void WriteInManagedLockReleaseForSuspendHandler(Assembler asm, Operand ctx, Operand tmp, Operand tmp2, Operand lblAgain)
        {
            Operand tmpUint = new(tmp.GetRegister().Index, RegisterType.Integer, OperandType.I32);
            Operand tmp2Uint = new(tmp2.GetRegister().Index, RegisterType.Integer, OperandType.I32);

            Operand lblLoop = asm.CreateLabel();
            Operand lblInterrupt = asm.CreateLabel();
            Operand lblDone = asm.CreateLabel();

            // Bit 0 set means that the thread is currently executing managed code (it should be always set here, as we just returned from managed code).
            // Bit 1 being set means a interrupt was requested while it was in managed, we should service it.

            // Use ctx temporarily as the state address. tmp is a separate status register;
            // STLXR must not use the same architectural register for its base and status.
            asm.Add(ctx, ctx, Const((ulong)NceNativeContext.GetInManagedOffset()));
            asm.MarkLabel(lblLoop);
            asm.Ldaxr(tmp2Uint, ctx);
            asm.Cmp(tmp2Uint, Const(OperandType.I32, 3));
            asm.B(lblInterrupt, ArmCondition.Eq);
            asm.Stlxr(Gpr(Assembler.ZrRegister, OperandType.I32), ctx, tmpUint);
            asm.Cbnz(tmpUint, lblLoop); // Retry if store failed.
            asm.Sub(ctx, ctx, Const((ulong)NceNativeContext.GetInManagedOffset())); // Restore the context before any exit/callback.
            asm.B(lblDone);

            asm.MarkLabel(lblInterrupt);

            // If we got here, a interrupt was requested while it was in managed code.
            // Let's service the interrupt and check what we should do next.

            asm.Mov(tmp2Uint, Const(OperandType.I32, 1));
            asm.Stlxr(tmp2Uint, ctx, tmpUint);
            asm.Cbnz(tmpUint, lblLoop); // Retry if store failed.
            asm.Sub(ctx, ctx, Const((ulong)NceNativeContext.GetInManagedOffset())); // Restore the context before any exit/callback.
            asm.B(lblAgain);

            asm.MarkLabel(lblDone);
        }

        // Host tests surround the actual helpers with synthetic context/callbacks; production never calls this.
        internal static uint[] GenerateInManagedProtocolForTest(int operation, nint nativeContextPtr,
            nint interruptHandlerPtr, int contextReg, int statusReg, int valueReg)
        {
            Assembler asm = new();
            CreateRegisterSaveRestoreForManaged().WritePrologue(asm);
            Operand ctx = Gpr(contextReg);
            Operand status = Gpr(statusReg);
            Operand value = Gpr(valueReg);
            Operand quit = asm.CreateLabel();
            Operand done = asm.CreateLabel();
            asm.Mov(ctx, (ulong)nativeContextPtr);
            if (operation == 0)
            {
                WriteInManagedLockAcquire(asm, ctx, status, value);
            }
            else if (operation == 4)
            {
                Operand again = asm.CreateLabel();
                Operand retry = asm.CreateLabel();
                asm.MarkLabel(retry);
                WriteInManagedLockReleaseForSuspendHandler(asm, ctx, status, value, again);
                asm.B(done);
                asm.MarkLabel(again);
                asm.Mov(status, (ulong)interruptHandlerPtr);
                asm.Blr(status);
                asm.B(retry);
            }
            else
            {
                ThreadExitMethod method = operation switch
                {
                    1 => ThreadExitMethod.None,
                    2 => ThreadExitMethod.GenerateReturn,
                    3 => ThreadExitMethod.Label,
                    _ => throw new ArgumentOutOfRangeException(nameof(operation)),
                };
                WriteInManagedLockRelease(asm, ctx, status, value, method, quit, interruptHandlerPtr);
            }
            asm.MarkLabel(done);
            asm.Mov(Gpr(0), ctx); // Verifies the helper returns a base, not base+InManagedOffset.
            CreateRegisterSaveRestoreForManaged().WriteEpilogue(asm);
            asm.Ret(Gpr(30));
            asm.MarkLabel(quit);
            asm.Mov(Gpr(0), 0xeeeeUL);
            CreateRegisterSaveRestoreForManaged().WriteEpilogue(asm);
            asm.Ret(Gpr(30));
            return asm.GetCode();
        }

        private static RegisterSaveRestore CreateRegisterSaveRestoreForManaged()
        {
            return new RegisterSaveRestore((int)IntCalleeSavedRegsMask, unchecked((int)FpCalleeSavedRegsMask), OperandType.FP64, hasCall: true);
        }
    }
}
