using LibHac;
using LibHac.Common;
using LibHac.Fs;
using LibHac.Fs.Fsa;
using LibHac.FsSystem;
using LibHac.Tools.Fs;
using LibHac.Tools.FsSystem;
using Ryujinx.Common.Configuration;
using Ryujinx.Common.Logging;
using System;
using System.Buffers.Binary;
using System.Collections.Generic;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using ZstdSharp;
using Path = System.IO.Path;

namespace Ryujinx.HLE.FileSystem
{
    /// <summary>
    /// NEOV2-NCZ1: leitura de NSZ/XCZ.
    /// O container (PFS0 do NSZ ou particao secure do XCZ) e reconstruido como um PFS0 virtual
    /// em que cada "xxx.ncz" aparece como "xxx.nca" com o conteudo original (descomprimido e recriptografado).
    /// O resto do emulador continua enxergando um NSP comum.
    /// </summary>
    public static class NczSupport
    {
        public static bool IsXciExtension(string extension)
        {
            string ext = (extension ?? string.Empty).TrimStart('.').ToLowerInvariant();

            return ext is "xci" or "xcz";
        }

        /// <summary>Converte "nsz" em "nsp" e "xcz" em "xci" (com ou sem ponto, preserva o ponto).</summary>
        public static string NormalizeExtension(string extension)
        {
            if (extension == null)
            {
                return null;
            }

            bool hasDot = extension.StartsWith('.');
            string ext = extension.TrimStart('.').ToLowerInvariant();

            ext = ext switch
            {
                "nsz" => "nsp",
                "xcz" => "xci",
                _ => ext,
            };

            return hasDot ? "." + ext : ext;
        }

        public static bool HasNczEntries(IFileSystem fileSystem)
        {
            if (fileSystem == null)
            {
                return false;
            }

            foreach (DirectoryEntryEx entry in fileSystem.EnumerateEntries("/", "*"))
            {
                if (entry.Type == DirectoryEntryType.File && entry.Name.EndsWith(".ncz", StringComparison.OrdinalIgnoreCase))
                {
                    return true;
                }
            }

            return false;
        }

        public static IFileSystem WrapIfCompressed(IFileSystem source)
        {
            return TryWrap(source, out PartitionFileSystem wrapped) ? wrapped : source;
        }

        public static bool TryWrap(IFileSystem source, out PartitionFileSystem wrapped)
        {
            wrapped = null;

            if (!HasNczEntries(source))
            {
                return false;
            }

            List<VirtualPfsFile.Entry> entries = [];
            int nczCount = 0;

            foreach (DirectoryEntryEx entry in source.EnumerateEntries("/", "*"))
            {
                if (entry.Type != DirectoryEntryType.File)
                {
                    continue;
                }

                using UniqueRef<IFile> fileRef = new();

                source.OpenFile(ref fileRef.Ref, entry.FullPath.ToU8Span(), OpenMode.Read).ThrowIfFailure();

                IFile file = fileRef.Release();

                if (entry.Name.EndsWith(".ncz", StringComparison.OrdinalIgnoreCase))
                {
                    NczFile ncz = new(file, entry.Name);
                    string ncaName = entry.Name.Substring(0, entry.Name.Length - 4) + ".nca";

                    entries.Add(new VirtualPfsFile.Entry(ncaName, ncz, ncz.NcaSize));
                    nczCount++;
                }
                else
                {
                    entries.Add(new VirtualPfsFile.Entry(entry.Name, file, entry.Size));
                }
            }

            VirtualPfsFile pfsFile = new(entries);
            PartitionFileSystem pfs = new();

            pfs.Initialize(pfsFile.AsStorage()).ThrowIfFailure();

            Logger.Info?.Print(LogClass.Loader, $"[NEOV2-NCZ1] Compressed container opened: {nczCount} NCZ, {entries.Count} files");

            wrapped = pfs;

            return true;
        }
    }

    /// <summary>
    /// NEOV2-NCZ1: arquivo PFS0 montado em memoria (cabecalho) + arquivos de origem lidos sob demanda.
    /// </summary>
    internal sealed class VirtualPfsFile : IFile
    {
        internal readonly struct Entry
        {
            public readonly string Name;
            public readonly IFile File;
            public readonly long Size;

            public Entry(string name, IFile file, long size)
            {
                Name = name;
                File = file;
                Size = size;
            }
        }

        private readonly byte[] _header;
        private readonly Entry[] _entries;
        private readonly long[] _starts;
        private readonly long _totalSize;

        public VirtualPfsFile(List<Entry> entries)
        {
            _entries = entries.ToArray();

            using MemoryStream names = new();
            int[] nameOffsets = new int[_entries.Length];

            for (int i = 0; i < _entries.Length; i++)
            {
                nameOffsets[i] = (int)names.Length;

                byte[] nameBytes = Encoding.UTF8.GetBytes(_entries[i].Name);

                names.Write(nameBytes, 0, nameBytes.Length);
                names.WriteByte(0);
            }

            int headerBase = 0x10 + (0x18 * _entries.Length);
            int stringTableSize = (int)names.Length;
            int totalHeader = headerBase + stringTableSize;
            int alignedHeader = (totalHeader + 0x1F) & ~0x1F;

            stringTableSize += alignedHeader - totalHeader;

            _header = new byte[alignedHeader];

            Span<byte> h = _header;

            Encoding.ASCII.GetBytes("PFS0").CopyTo(h);
            BinaryPrimitives.WriteInt32LittleEndian(h[0x4..], _entries.Length);
            BinaryPrimitives.WriteInt32LittleEndian(h[0x8..], stringTableSize);

            _starts = new long[_entries.Length];

            long dataOffset = 0;

            for (int i = 0; i < _entries.Length; i++)
            {
                Span<byte> e = h.Slice(0x10 + (0x18 * i), 0x18);

                BinaryPrimitives.WriteInt64LittleEndian(e, dataOffset);
                BinaryPrimitives.WriteInt64LittleEndian(e[0x8..], _entries[i].Size);
                BinaryPrimitives.WriteInt32LittleEndian(e[0x10..], nameOffsets[i]);

                _starts[i] = _header.Length + dataOffset;
                dataOffset += _entries[i].Size;
            }

            names.ToArray().CopyTo(h[headerBase..]);

            _totalSize = _header.Length + dataOffset;
        }

        protected override Result DoRead(out long bytesRead, long offset, Span<byte> destination, in ReadOption option)
        {
            bytesRead = 0;

            if (offset >= _totalSize || destination.Length == 0)
            {
                return Result.Success;
            }

            long toRead = Math.Min(destination.Length, _totalSize - offset);
            long position = offset;
            int written = 0;

            while (written < toRead)
            {
                int remaining = (int)(toRead - written);

                if (position < _header.Length)
                {
                    int count = (int)Math.Min(remaining, _header.Length - position);

                    _header.AsSpan((int)position, count).CopyTo(destination.Slice(written, count));

                    position += count;
                    written += count;

                    continue;
                }

                int index = FindEntry(position);
                Entry entry = _entries[index];
                long inFile = position - _starts[index];
                int chunk = (int)Math.Min(remaining, entry.Size - inFile);

                if (chunk <= 0)
                {
                    break;
                }

                Result rc = entry.File.Read(out long fileRead, inFile, destination.Slice(written, chunk));

                if (rc.IsFailure())
                {
                    return rc;
                }

                if (fileRead <= 0)
                {
                    break;
                }

                position += fileRead;
                written += (int)fileRead;
            }

            bytesRead = written;

            return Result.Success;
        }

        private int FindEntry(long position)
        {
            int lo = 0;
            int hi = _entries.Length - 1;

            while (lo < hi)
            {
                int mid = (lo + hi + 1) >> 1;

                if (_starts[mid] <= position)
                {
                    lo = mid;
                }
                else
                {
                    hi = mid - 1;
                }
            }

            // Pula arquivos de tamanho zero.
            while (lo < _entries.Length - 1 && position >= _starts[lo] + _entries[lo].Size)
            {
                lo++;
            }

            return lo;
        }

        protected override Result DoWrite(long offset, ReadOnlySpan<byte> source, in WriteOption option) => throw new NotSupportedException();

        protected override Result DoFlush() => Result.Success;

        protected override Result DoSetSize(long size) => throw new NotSupportedException();

        protected override Result DoGetSize(out long size)
        {
            size = _totalSize;

            return Result.Success;
        }

        protected override Result DoOperateRange(Span<byte> outBuffer, OperationId operationId, long offset, long size, ReadOnlySpan<byte> inBuffer) => throw new NotSupportedException();
    }

    /// <summary>
    /// NEOV2-NCZ1: NCZ exposto como o NCA original.
    /// Layout NCZ: 0x4000 bytes do NCA intactos, "NCZSECTN" com as secoes, opcional "NCZBLOCK", dados zstd.
    /// Os dados descomprimidos equivalem ao NCA a partir de 0x4000, com as secoes decriptadas;
    /// aqui sao recriptografados em AES-CTR (tipos 3 e 4), como o nsz faz na descompressao.
    /// Modo bloco: leitura aleatoria por bloco. Modo solido: descomprime uma vez para um cache em disco.
    /// </summary>
    internal sealed class NczFile : IFile
    {
        private const int UncompressableHeaderSize = 0x4000;
        private const int BlockCacheCapacity = 8;

        private readonly struct Section
        {
            public readonly long Offset;
            public readonly long Size;
            public readonly long CryptoType;
            public readonly byte[] Key;
            public readonly byte[] Counter;

            public Section(long offset, long size, long cryptoType, byte[] key, byte[] counter)
            {
                Offset = offset;
                Size = size;
                CryptoType = cryptoType;
                Key = key;
                Counter = counter;
            }
        }

        private readonly object _lock = new();
        private readonly IFile _base;
        private readonly string _name;
        private readonly long _baseSize;
        private readonly byte[] _header;
        private readonly Section[] _sections;
        private readonly Aes[] _aes;
        private readonly long _dataOffset;

        // Modo bloco
        private readonly bool _isBlock;
        private readonly int _blockSize;
        private readonly long[] _blockOffsets;
        private readonly int[] _blockCompressedSizes;
        private readonly Dictionary<int, byte[]> _blockCache = new();
        private readonly LinkedList<int> _blockOrder = new();
        private Decompressor _decompressor;

        // Modo solido
        private System.IO.FileStream _solidCache;

        public long NcaSize { get; }

        public NczFile(IFile baseFile, string name)
        {
            _base = baseFile;
            _name = name;

            _base.GetSize(out _baseSize).ThrowIfFailure();

            _header = new byte[UncompressableHeaderSize];
            ReadBaseExact(0, _header);

            byte[] sectHeader = new byte[0x10];
            ReadBaseExact(UncompressableHeaderSize, sectHeader);

            if (Encoding.ASCII.GetString(sectHeader, 0, 8) != "NCZSECTN")
            {
                throw new InvalidDataException($"NCZ invalido (sem NCZSECTN): {name}");
            }

            long sectionCount = BinaryPrimitives.ReadInt64LittleEndian(sectHeader.AsSpan(8));

            if (sectionCount <= 0 || sectionCount > 0x100)
            {
                throw new InvalidDataException($"NCZ invalido (secoes={sectionCount}): {name}");
            }

            byte[] sectionData = new byte[sectionCount * 0x40];
            ReadBaseExact(UncompressableHeaderSize + 0x10, sectionData);

            _sections = new Section[sectionCount];
            _aes = new Aes[sectionCount];

            long expected = UncompressableHeaderSize;

            for (int i = 0; i < sectionCount; i++)
            {
                ReadOnlySpan<byte> s = sectionData.AsSpan(i * 0x40, 0x40);

                long offset = BinaryPrimitives.ReadInt64LittleEndian(s);
                long size = BinaryPrimitives.ReadInt64LittleEndian(s[0x8..]);
                long cryptoType = BinaryPrimitives.ReadInt64LittleEndian(s[0x10..]);

                if (offset < UncompressableHeaderSize || size < 0)
                {
                    throw new InvalidDataException($"NCZ com secao invalida: {name}");
                }

                _sections[i] = new Section(offset, size, cryptoType, s.Slice(0x20, 0x10).ToArray(), s.Slice(0x30, 0x10).ToArray());

                if (cryptoType is 3 or 4)
                {
                    Aes aes = Aes.Create();
                    aes.Key = _sections[i].Key;
                    _aes[i] = aes;
                }

                expected = Math.Max(expected, offset + size);
            }

            long position = UncompressableHeaderSize + 0x10 + sectionData.Length;
            byte[] blockHeader = new byte[0x18];

            if (position + blockHeader.Length <= _baseSize)
            {
                ReadBaseExact(position, blockHeader);
            }

            if (Encoding.ASCII.GetString(blockHeader, 0, 8) == "NCZBLOCK")
            {
                int exponent = blockHeader[0xB];
                int blockCount = BinaryPrimitives.ReadInt32LittleEndian(blockHeader.AsSpan(0xC));
                long decompressedSize = BinaryPrimitives.ReadInt64LittleEndian(blockHeader.AsSpan(0x10));

                if (exponent < 14 || exponent > 30 || blockCount <= 0 || UncompressableHeaderSize + decompressedSize < expected)
                {
                    throw new InvalidDataException($"NCZBLOCK invalido: {name}");
                }

                byte[] sizes = new byte[blockCount * 4];
                ReadBaseExact(position + 0x18, sizes);

                _isBlock = true;
                _blockSize = 1 << exponent;
                _blockCompressedSizes = new int[blockCount];
                _blockOffsets = new long[blockCount];

                long dataStart = position + 0x18 + sizes.Length;
                long current = dataStart;

                for (int i = 0; i < blockCount; i++)
                {
                    _blockCompressedSizes[i] = BinaryPrimitives.ReadInt32LittleEndian(sizes.AsSpan(i * 4));
                    _blockOffsets[i] = current;
                    current += _blockCompressedSizes[i];
                }

                _dataOffset = dataStart;
                NcaSize = UncompressableHeaderSize + decompressedSize;
            }
            else
            {
                _isBlock = false;
                _dataOffset = position;
                NcaSize = expected;
            }
        }

        private void ReadBaseExact(long offset, Span<byte> destination)
        {
            int done = 0;

            while (done < destination.Length)
            {
                _base.Read(out long read, offset + done, destination[done..]).ThrowIfFailure();

                if (read <= 0)
                {
                    throw new EndOfStreamException($"NCZ truncado: {_name}");
                }

                done += (int)read;
            }
        }

        protected override Result DoRead(out long bytesRead, long offset, Span<byte> destination, in ReadOption option)
        {
            bytesRead = 0;

            if (offset >= NcaSize || destination.Length == 0)
            {
                return Result.Success;
            }

            int toRead = (int)Math.Min(destination.Length, NcaSize - offset);
            Span<byte> output = destination[..toRead];

            lock (_lock)
            {
                long position = offset;
                int written = 0;

                if (position < UncompressableHeaderSize)
                {
                    int count = (int)Math.Min(toRead, UncompressableHeaderSize - position);

                    _header.AsSpan((int)position, count).CopyTo(output);

                    position += count;
                    written += count;
                }

                if (written < toRead)
                {
                    Span<byte> body = output[written..];

                    ReadDecompressed(position - UncompressableHeaderSize, body);
                    Encrypt(position, body);
                }
            }

            bytesRead = toRead;

            return Result.Success;
        }

        private void ReadDecompressed(long offset, Span<byte> destination)
        {
            if (_isBlock)
            {
                int done = 0;

                while (done < destination.Length)
                {
                    long current = offset + done;
                    int blockIndex = (int)(current / _blockSize);
                    int inBlock = (int)(current % _blockSize);
                    byte[] block = GetBlock(blockIndex);
                    int count = Math.Min(destination.Length - done, block.Length - inBlock);

                    block.AsSpan(inBlock, count).CopyTo(destination[done..]);
                    done += count;
                }
            }
            else
            {
                EnsureSolidCache();

                _solidCache.Position = offset;
                _solidCache.ReadExactly(destination);
            }
        }

        private byte[] GetBlock(int index)
        {
            if (_blockCache.TryGetValue(index, out byte[] cached))
            {
                _blockOrder.Remove(index);
                _blockOrder.AddFirst(index);

                return cached;
            }

            long totalDecompressed = NcaSize - UncompressableHeaderSize;
            int decompressedSize = (int)Math.Min(_blockSize, totalDecompressed - ((long)index * _blockSize));
            int compressedSize = _blockCompressedSizes[index];

            byte[] compressed = new byte[compressedSize];
            ReadBaseExact(_blockOffsets[index], compressed);

            byte[] block;

            if (compressedSize < decompressedSize)
            {
                _decompressor ??= new Decompressor();

                block = new byte[decompressedSize];

                int produced = _decompressor.Unwrap(new ReadOnlySpan<byte>(compressed), new Span<byte>(block));

                if (produced != decompressedSize)
                {
                    throw new InvalidDataException($"NCZ bloco {index} descomprimiu {produced} de {decompressedSize} bytes: {_name}");
                }
            }
            else
            {
                block = compressed;
            }

            _blockCache[index] = block;
            _blockOrder.AddFirst(index);

            if (_blockOrder.Count > BlockCacheCapacity)
            {
                int oldest = _blockOrder.Last!.Value;

                _blockOrder.RemoveLast();
                _blockCache.Remove(oldest);
            }

            return block;
        }

        private void EnsureSolidCache()
        {
            if (_solidCache != null)
            {
                return;
            }

            long totalDecompressed = NcaSize - UncompressableHeaderSize;
            string cacheDir = Path.Combine(AppDataManager.BaseDirPath, "ncz_cache");
            string cachePath = Path.Combine(cacheDir, $"{_name}_{_baseSize}.bin");

            System.IO.Directory.CreateDirectory(cacheDir);

            if (!System.IO.File.Exists(cachePath) || new System.IO.FileInfo(cachePath).Length != totalDecompressed)
            {
                Logger.Info?.Print(LogClass.Loader, $"[NEOV2-NCZ1] Solid NCZ, decompressing to cache: {_name} ({totalDecompressed / (1024 * 1024)} MiB)");

                string partPath = cachePath + ".part";

                using (IFileReadStream input = new(_base, _dataOffset, _baseSize))
                using (DecompressionStream zstd = new(input))
                using (System.IO.FileStream output = new(partPath, System.IO.FileMode.Create, System.IO.FileAccess.Write, System.IO.FileShare.None, 1 << 20))
                {
                    byte[] buffer = new byte[1 << 20];
                    long remaining = totalDecompressed;

                    while (remaining > 0)
                    {
                        int read = zstd.Read(buffer, 0, (int)Math.Min(buffer.Length, remaining));

                        if (read <= 0)
                        {
                            throw new InvalidDataException($"NCZ solido terminou antes do esperado: {_name}");
                        }

                        output.Write(buffer, 0, read);
                        remaining -= read;
                    }
                }

                System.IO.File.Move(partPath, cachePath, true);

                Logger.Info?.Print(LogClass.Loader, $"[NEOV2-NCZ1] Solid NCZ cached: {_name}");
            }

            _solidCache = new System.IO.FileStream(cachePath, System.IO.FileMode.Open, System.IO.FileAccess.Read, System.IO.FileShare.Read, 1 << 16);
        }

        private void Encrypt(long ncaOffset, Span<byte> data)
        {
            long end = ncaOffset + data.Length;

            for (int i = 0; i < _sections.Length; i++)
            {
                Section section = _sections[i];

                if (_aes[i] == null)
                {
                    continue;
                }

                long start = Math.Max(ncaOffset, section.Offset);
                long stop = Math.Min(end, section.Offset + section.Size);

                if (start >= stop)
                {
                    continue;
                }

                ApplyCtr(_aes[i], section.Counter, start, data.Slice((int)(start - ncaOffset), (int)(stop - start)));
            }
        }

        internal static void ApplyCtr(Aes aes, byte[] sectionCounter, long ncaOffset, Span<byte> data)
        {
            long firstBlock = ncaOffset >> 4;
            int skip = (int)(ncaOffset & 0xF);
            int blockCount = (skip + data.Length + 15) >> 4;

            byte[] counters = new byte[blockCount * 16];

            for (int b = 0; b < blockCount; b++)
            {
                Span<byte> c = counters.AsSpan(b * 16, 16);

                sectionCounter.AsSpan(0, 8).CopyTo(c);
                BinaryPrimitives.WriteInt64BigEndian(c[8..], firstBlock + b);
            }

            byte[] keystream = aes.EncryptEcb(counters, PaddingMode.None);

            for (int i = 0; i < data.Length; i++)
            {
                data[i] ^= keystream[skip + i];
            }
        }

        protected override Result DoWrite(long offset, ReadOnlySpan<byte> source, in WriteOption option) => throw new NotSupportedException();

        protected override Result DoFlush() => Result.Success;

        protected override Result DoSetSize(long size) => throw new NotSupportedException();

        protected override Result DoGetSize(out long size)
        {
            size = NcaSize;

            return Result.Success;
        }

        protected override Result DoOperateRange(Span<byte> outBuffer, OperationId operationId, long offset, long size, ReadOnlySpan<byte> inBuffer) => throw new NotSupportedException();
    }

    /// <summary>
    /// NEOV2-NCZ1: Stream sequencial (somente leitura) sobre um IFile, a partir de um offset.
    /// </summary>
    internal sealed class IFileReadStream : Stream
    {
        private readonly IFile _file;
        private readonly long _end;
        private long _position;

        public IFileReadStream(IFile file, long start, long end)
        {
            _file = file;
            _position = start;
            _end = end;
        }

        public override bool CanRead => true;
        public override bool CanSeek => false;
        public override bool CanWrite => false;
        public override long Length => throw new NotSupportedException();

        public override long Position
        {
            get => throw new NotSupportedException();
            set => throw new NotSupportedException();
        }

        public override int Read(byte[] buffer, int offset, int count) => Read(buffer.AsSpan(offset, count));

        public override int Read(Span<byte> buffer)
        {
            int count = (int)Math.Min(buffer.Length, _end - _position);

            if (count <= 0)
            {
                return 0;
            }

            _file.Read(out long read, _position, buffer[..count]).ThrowIfFailure();

            _position += read;

            return (int)read;
        }

        public override void Flush() { }
        public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
        public override void SetLength(long value) => throw new NotSupportedException();
        public override void Write(byte[] buffer, int offset, int count) => throw new NotSupportedException();
    }
}
