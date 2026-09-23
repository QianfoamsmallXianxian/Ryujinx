using Ryujinx.Common;
using Ryujinx.Memory;
using System.Collections.Generic;
using System.Diagnostics;
using System.Runtime.InteropServices;

namespace Ryujinx.Cpu.Nce
{
    /// <summary>
    /// Native Code Execution CPU code patch.
    /// </summary>
    public class NceCpuCodePatch
    {
        private readonly List<uint> _code;

        private readonly struct PatchTarget
        {
            public readonly int TextIndex;
            public readonly int PatchStartIndex;
            public readonly int PatchBranchIndex;

            public PatchTarget(int textIndex, int patchStartIndex, int patchBranchIndex)
            {
                TextIndex = textIndex;
                PatchStartIndex = patchStartIndex;
                PatchBranchIndex = patchBranchIndex;
            }
        }

        private readonly List<PatchTarget> _patchTargets;

        // NEOV2 NCE-EXCL1: instructions rewritten in place in the text section, without a trampoline.
        private readonly List<(int TextIndex, uint Instruction)> _inPlaceInstructions;

        /// <inheritdoc/>
        public ulong Size => BitUtils.AlignUp((ulong)_code.Count * sizeof(uint), 0x1000UL);

        public NceCpuCodePatch()
        {
            _code = new();
            _patchTargets = new();
            _inPlaceInstructions = new();
        }

        /// <summary>
        /// NEOV2 NCE-EXCL1: replaces one instruction of the text section with another of the same size.
        /// </summary>
        /// <param name="textIndex">Index of the instruction in the text section, in 32-bit words</param>
        /// <param name="instruction">Replacement instruction</param>
        internal void ReplaceInstruction(int textIndex, uint instruction)
        {
            _inPlaceInstructions.Add((textIndex, instruction));
        }

        internal void AddCode(int textIndex, IEnumerable<uint> code)
        {
            int patchStartIndex = _code.Count;
            _code.AddRange(code);
            _patchTargets.Add(new PatchTarget(textIndex, patchStartIndex, _code.Count - 1));
        }

        /// <inheritdoc/>
        public void Write(IVirtualMemoryManager memoryManager, ulong patchAddress, ulong textAddress)
        {
            uint[] code = _code.ToArray();

            foreach ((int textIndex, uint instruction) in _inPlaceInstructions)
            {
                memoryManager.Write(textAddress + (ulong)textIndex * sizeof(uint), instruction);
            }

            foreach (PatchTarget patchTarget in _patchTargets)
            {
                ulong instPatchStartAddress = patchAddress + (ulong)patchTarget.PatchStartIndex * sizeof(uint);
                ulong instPatchBranchAddress = patchAddress + (ulong)patchTarget.PatchBranchIndex * sizeof(uint);
                ulong instTextAddress = textAddress + (ulong)patchTarget.TextIndex * sizeof(uint);

                uint prevInst = memoryManager.Read<uint>(instTextAddress);

                code[patchTarget.PatchBranchIndex] |= EncodeSImm26_2(checked((int)((long)instTextAddress - (long)instPatchBranchAddress + sizeof(uint))));
                memoryManager.Write(instTextAddress, 0x14000000u | EncodeSImm26_2(checked((int)((long)instPatchStartAddress - (long)instTextAddress))));

                uint newInst = memoryManager.Read<uint>(instTextAddress);
            }

            if (Size != 0)
            {
                memoryManager.Write(patchAddress, MemoryMarshal.Cast<uint, byte>(code));
                memoryManager.Reprotect(patchAddress, Size, MemoryPermission.ReadAndExecute);
            }
        }

        private static uint EncodeSImm26_2(int value)
        {
            uint imm = (uint)(value >> 2) & 0x3ffffff;
            Debug.Assert(((int)imm << 6) >> 4 == value, $"Failed to encode constant 0x{value:X}.");
            return imm;
        }
    }
}