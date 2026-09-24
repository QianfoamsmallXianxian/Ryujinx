using Ryujinx.Common.Logging;
using System;
using System.Collections.Generic;
using System.IO;
using System.Runtime.InteropServices;

namespace Ryujinx.Graphics.GAL.Multithreading
{
    /// <summary>
    /// NEOV2 BACKEND-PRIME1: fixa a thread do backend Vulkan (RenderLoop) no(s) nucleo(s) mais rapido(s) do aparelho.
    /// No MUA3 (AYN Thor), os quadros atrasados sao os que o backend precisa de mais de 33 ms de CPU, e ele so
    /// rodava ~19% do tempo no nucleo principal. So atua no Android/Linux e quando existem nucleos de velocidades
    /// diferentes; qualquer falha apenas mantem o comportamento padrao do sistema.
    /// </summary>
    static class BackendThreadAffinity
    {
        private const string CpuRoot = "/sys/devices/system/cpu";

        [DllImport("libc", SetLastError = true)]
        private static extern unsafe int sched_setaffinity(int pid, nuint cpusetsize, ulong* mask);

        public static unsafe void PinCurrentThreadToFastestCores()
        {
            if (!OperatingSystem.IsAndroid() && !OperatingSystem.IsLinux())
            {
                return;
            }

            try
            {
                List<(int Cpu, long MaxFreq)> cpus = new();

                foreach (string dir in Directory.GetDirectories(CpuRoot, "cpu*"))
                {
                    string name = Path.GetFileName(dir);

                    if (name.Length <= 3 || !int.TryParse(name.AsSpan(3), out int cpu) || cpu >= 64)
                    {
                        continue;
                    }

                    string freqPath = Path.Combine(dir, "cpufreq", "cpuinfo_max_freq");

                    if (File.Exists(freqPath) && long.TryParse(File.ReadAllText(freqPath).Trim(), out long maxFreq))
                    {
                        cpus.Add((cpu, maxFreq));
                    }
                }

                if (cpus.Count < 2)
                {
                    Logger.Info?.Print(LogClass.Gpu, "[NEOV2-BACKEND-PRIME1] Not applied: CPU frequency information unavailable");
                    return;
                }

                long fastest = 0;
                long slowest = long.MaxValue;

                foreach ((int _, long maxFreq) in cpus)
                {
                    fastest = Math.Max(fastest, maxFreq);
                    slowest = Math.Min(slowest, maxFreq);
                }

                if (fastest == slowest)
                {
                    Logger.Info?.Print(LogClass.Gpu, "[NEOV2-BACKEND-PRIME1] Not applied: all cores have the same maximum frequency");
                    return;
                }

                ulong mask = 0;
                List<int> selected = new();

                foreach ((int cpu, long maxFreq) in cpus)
                {
                    if (maxFreq == fastest)
                    {
                        mask |= 1UL << cpu;
                        selected.Add(cpu);
                    }
                }

                selected.Sort();

                // pid 0 = thread atual.
                int result = sched_setaffinity(0, (nuint)sizeof(ulong), &mask);

                if (result == 0)
                {
                    Logger.Info?.Print(LogClass.Gpu, $"[NEOV2-BACKEND-PRIME1] Vulkan backend thread pinned to CPU {string.Join(",", selected)} ({fastest / 1000} MHz max)");
                }
                else
                {
                    Logger.Info?.Print(LogClass.Gpu, $"[NEOV2-BACKEND-PRIME1] Not applied: sched_setaffinity failed (errno {Marshal.GetLastPInvokeError()})");
                }
            }
            catch (Exception ex)
            {
                Logger.Info?.Print(LogClass.Gpu, $"[NEOV2-BACKEND-PRIME1] Not applied: {ex.GetType().Name}: {ex.Message}");
            }
        }
    }
}
