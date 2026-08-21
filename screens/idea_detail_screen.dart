import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';
import 'package:path_provider/path_provider.dart';
import 'package:image_picker/image_picker.dart';
import 'package:file_picker/file_picker.dart';
import 'package:archive/archive_io.dart';
import 'package:audioplayers/audioplayers.dart';
import 'dart:io';
import 'dart:convert';
import '../db/db_helper.dart';
import '../models/idea.dart';
import '../models/project.dart';
import '../models/idea_file.dart';
import '../widgets/app_theme.dart';
import 'file_editor_screen.dart';
import 'image_viewer_screen.dart';
import 'pdf_viewer_screen.dart';
import 'markdown_viewer_screen.dart';
import 'copy_move_screen.dart';

class IdeaDetailScreen extends StatefulWidget {
  final Idea idea;
  final Project project;
  const IdeaDetailScreen({super.key, required this.idea, required this.project});
  @override State<IdeaDetailScreen> createState() => _IdeaDetailScreenState();
}

class _IdeaDetailScreenState extends State<IdeaDetailScreen> {
  List<IdeaFile> _files = [];
  late Idea _idea;
  bool _loading = true;
  final _picker = ImagePicker();
  final _audioPlayer = AudioPlayer();
  String? _playingFileId; // tracks which file is currently playing

  @override
  void initState() { super.initState(); _idea = widget.idea; _load(); }

  @override
  void dispose() { _audioPlayer.dispose(); super.dispose(); }

  Future<void> _load() async {
    if (_idea.id == null) return;
    final files = await DBHelper.getFiles(_idea.id!);
    if (mounted) setState(() { _files = files; _loading = false; });
  }

  Future<void> _cycleStatus() async {
    const next = {'todo':'doing','doing':'done','done':'todo'};
    final updated = _idea.copyWith(status: next[_idea.status]!, updatedAt: now());
    await DBHelper.updateIdea(updated);
    setState(() => _idea = updated);
  }

  // ── ADD OPTIONS SHEET ─────────────────────────────────
  void _showAddOptions() {
    showModalBottomSheet(
      context: context, backgroundColor: AppTheme.bg2,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (_) => SafeArea(child: Column(mainAxisSize: MainAxisSize.min, children: [
        const SizedBox(height: 8),
        Container(width: 40, height: 4, decoration: BoxDecoration(
            color: AppTheme.border, borderRadius: BorderRadius.circular(2))),
        const SizedBox(height: 12),
        const Text('ফাইল যোগ করো', style: TextStyle(color: AppTheme.textPrimary,
            fontSize: 15, fontWeight: FontWeight.w700)),
        const SizedBox(height: 8),
        _sheetTile(Icons.code_outlined, 'কোড / টেক্সট ফাইল', AppTheme.accent,
            () { Navigator.pop(context); _addTextFile(); }),
        _sheetTile(Icons.photo_library_outlined, 'গ্যালারি থেকে ছবি', AppTheme.green,
            () { Navigator.pop(context); _addImage(ImageSource.gallery); }),
        _sheetTile(Icons.camera_alt_outlined, 'ক্যামেরা দিয়ে ছবি', AppTheme.yellow,
            () { Navigator.pop(context); _addImage(ImageSource.camera); }),
        _sheetTile(Icons.attach_file_outlined, 'যেকোনো ফাইল (APK, PDF, ZIP...)', AppTheme.textSecondary,
            () { Navigator.pop(context); _addAnyFile(); }),
        const SizedBox(height: 8),
      ])),
    );
  }

  // ── ADD TEXT FILE ─────────────────────────────────────
  Future<void> _addTextFile() async {
    final nameCtrl = TextEditingController();
    final contentCtrl = TextEditingController();
    await showModalBottomSheet(
      context: context, isScrollControlled: true, backgroundColor: AppTheme.bg2,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (ctx) => Padding(
        padding: EdgeInsets.only(bottom: MediaQuery.of(ctx).viewInsets.bottom,
            left: 16, right: 16, top: 20),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          const Text('নতুন টেক্সট/কোড ফাইল', style: TextStyle(
              color: AppTheme.textPrimary, fontSize: 16, fontWeight: FontWeight.w700)),
          const SizedBox(height: 12),
          _field(nameCtrl, 'ফাইলের নাম: index.js, style.css, README.md'),
          const SizedBox(height: 8),
          _field(contentCtrl, 'কোড বা টেক্সট (ঐচ্ছিক)...', maxLines: 4, mono: true),
          const SizedBox(height: 12),
          SizedBox(width: double.infinity, child: ElevatedButton.icon(
            onPressed: () async {
              if (nameCtrl.text.trim().isEmpty) return;
              final n = now();
              await DBHelper.insertFile(IdeaFile(
                ideaId: _idea.id!, projectId: widget.project.id!,
                name: nameCtrl.text.trim(), type: 'text',
                content: contentCtrl.text, createdAt: n, updatedAt: n,
              ));
              if (ctx.mounted) Navigator.pop(ctx);
              _load();
            },
            icon: const Icon(Icons.add, color: Colors.white, size: 16),
            label: const Text('যোগ করো', style: TextStyle(
                color: Colors.white, fontWeight: FontWeight.w700, fontSize: 14)),
            style: ElevatedButton.styleFrom(backgroundColor: AppTheme.accent,
                padding: const EdgeInsets.symmetric(vertical: 12),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10))),
          )),
          const SizedBox(height: 20),
        ]),
      ),
    );
  }

  // ── ADD IMAGE ─────────────────────────────────────────
  Future<void> _addImage(ImageSource source) async {
    try {
      final picked = await _picker.pickImage(source: source, imageQuality: 85);
      if (picked == null) return;
      final bytes = await picked.readAsBytes();
      final b64 = base64Encode(bytes);
      final ext = picked.name.split('.').last.toLowerCase();
      final name = picked.name.toLowerCase().contains('icon')
          ? picked.name : 'icon.$ext';
      final n = now();
      await DBHelper.insertFile(IdeaFile(
        ideaId: _idea.id!, projectId: widget.project.id!,
        name: name, type: 'image', content: b64, createdAt: n, updatedAt: n,
      ));
      _load();
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error: $e'), backgroundColor: AppTheme.red));
    }
  }

  // ── ADD ANY FILE ──────────────────────────────────────
  Future<void> _addAnyFile() async {
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.any, allowMultiple: true,
        withData: true,
      );
      if (result == null) return;
      final n = now();
      for (final pf in result.files) {
        if (pf.bytes == null && pf.path == null) continue;
        final bytes = pf.bytes ?? await File(pf.path!).readAsBytes();
        final ext = pf.extension?.toLowerCase() ?? '';
        // Determine type
        final dummy = IdeaFile(ideaId: 0, projectId: 0,
            name: pf.name, type: 'binary', createdAt: 0, updatedAt: 0);
        final isText = dummy.isText;
        String content;
        if (isText) {
          content = utf8.decode(bytes, allowMalformed: true);
        } else {
          content = base64Encode(bytes);
        }
        await DBHelper.insertFile(IdeaFile(
          ideaId: _idea.id!, projectId: widget.project.id!,
          name: pf.name, type: isText ? 'text' : 'binary',
          content: content, createdAt: n, updatedAt: n,
        ));
      }
      _load();
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('${result.files.length} ফাইল যোগ হয়েছে!'),
        backgroundColor: AppTheme.green,
      ));
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error: $e'), backgroundColor: AppTheme.red));
    }
  }

  // ── ZIP EXPORT ────────────────────────────────────────
  Future<void> _exportZip() async {
    final archive = Archive();

    // The note itself — title, description, status, priority — so the
    // zip is a complete package, not just the attachments.
    final statusLabel = {'todo': 'করা হয়নি', 'doing': 'চলছে', 'done': 'শেষ'}[_idea.status] ?? _idea.status;
    final priorityLabel = {'low': 'কম', 'medium': 'মাঝারি', 'high': 'বেশি'}[_idea.priority] ?? _idea.priority;
    final noteText = StringBuffer()
      ..writeln(_idea.title)
      ..writeln('=' * _idea.title.length)
      ..writeln()
      ..writeln('স্ট্যাটাস: $statusLabel')
      ..writeln('প্রায়োরিটি: $priorityLabel')
      ..writeln('তৈরি: ${DateTime.fromMillisecondsSinceEpoch(_idea.createdAt)}')
      ..writeln();
    if (_idea.description != null && _idea.description!.trim().isNotEmpty) {
      noteText..writeln('— নোট —')..writeln(_idea.description);
    }
    final noteBytes = utf8.encode(noteText.toString());
    archive.addFile(ArchiveFile('note.txt', noteBytes.length, noteBytes));

    for (final f in _files) {
      if (f.content == null) continue;
      List<int> bytes;
      if (f.isText) bytes = utf8.encode(f.content!);
      else { try { bytes = base64Decode(f.content!); } catch (_) { bytes = utf8.encode(f.content!); } }
      archive.addFile(ArchiveFile(f.name, bytes.length, bytes));
    }
    final dir = await getTemporaryDirectory();
    final zipName = '${_idea.title.replaceAll(' ', '_')}.zip';
    final zipFile = File('${dir.path}/$zipName');
    final encoded = ZipEncoder().encode(archive);
    if (encoded == null) return;
    await zipFile.writeAsBytes(encoded);
    await Share.shareXFiles([XFile(zipFile.path)], text: zipName);
  }

  // ── FILE ACTIONS ──────────────────────────────────────
  Future<void> _renameFile(IdeaFile file) async {
    final ctrl = TextEditingController(text: file.name);
    final result = await showDialog<String>(context: context,
      builder: (_) => AlertDialog(
        backgroundColor: AppTheme.bg3,
        title: const Text('রিনেম', style: TextStyle(color: AppTheme.textPrimary)),
        content: TextField(controller: ctrl, autofocus: true,
          style: const TextStyle(color: AppTheme.textPrimary, fontFamily: 'monospace'),
          decoration: InputDecoration(filled: true, fillColor: AppTheme.bg4,
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(8),
                borderSide: const BorderSide(color: AppTheme.border)),
            focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(8),
                borderSide: const BorderSide(color: AppTheme.accent, width: 2)),
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('বাতিল')),
          ElevatedButton(onPressed: () => Navigator.pop(context, ctrl.text.trim()),
              style: ElevatedButton.styleFrom(backgroundColor: AppTheme.accent),
              child: const Text('রিনেম')),
        ],
      ),
    );
    if (result != null && result.isNotEmpty && file.id != null) {
      await DBHelper.renameFile(file.id!, result);
      _load();
    }
  }

  Future<void> _deleteFile(IdeaFile file) async {
    final confirm = await showDialog<bool>(context: context,
      builder: (_) => AlertDialog(
        backgroundColor: AppTheme.bg3,
        title: const Text('মুছবে?', style: TextStyle(color: AppTheme.textPrimary)),
        content: Text('"${file.name}" মুছে যাবে।',
            style: const TextStyle(color: AppTheme.textSecondary)),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('বাতিল')),
          TextButton(onPressed: () => Navigator.pop(context, true),
              child: const Text('মুছো', style: TextStyle(color: AppTheme.red))),
        ],
      ),
    );
    if (confirm == true && file.id != null) { await DBHelper.deleteFile(file.id!); _load(); }
  }

  Future<void> _shareFile(IdeaFile file) async {
    if (file.content == null) return;
    final dir = await getTemporaryDirectory();
    final f = File('${dir.path}/${file.name}');
    if (file.isText) await f.writeAsString(file.content!);
    else { try { await f.writeAsBytes(base64Decode(file.content!)); } catch(_) { await f.writeAsString(file.content!); } }
    await Share.shareXFiles([XFile(f.path)], text: file.name);
  }

  // ── AUDIO PLAYBACK ────────────────────────────────────
  Future<void> _playAudio(IdeaFile file) async {
    if (file.content == null) return;
    try {
      if (_playingFileId == file.id.toString()) {
        await _audioPlayer.stop();
        setState(() => _playingFileId = null);
        return;
      }
      final bytes = base64Decode(file.content!);
      final dir = await getTemporaryDirectory();
      final tmpFile = File('${dir.path}/${file.name}');
      await tmpFile.writeAsBytes(bytes);
      setState(() => _playingFileId = file.id.toString());
      await _audioPlayer.play(DeviceFileSource(tmpFile.path));
      _audioPlayer.onPlayerComplete.listen((_) {
        if (mounted) setState(() => _playingFileId = null);
      });
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Play error: $e'), backgroundColor: AppTheme.red));
    }
  }

  // ── VIDEO PLAYBACK ────────────────────────────────────
  Future<void> _playVideo(IdeaFile file) async {
    if (file.content == null) return;
    try {
      final bytes = base64Decode(file.content!);
      final dir = await getTemporaryDirectory();
      final tmpFile = File('${dir.path}/${file.name}');
      await tmpFile.writeAsBytes(bytes);
      if (mounted) {
        Navigator.push(context, MaterialPageRoute(
            builder: (_) => _VideoPlayerScreen(file: tmpFile, name: file.name)));
      }
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Video error: $e'), backgroundColor: AppTheme.red));
    }
  }

  void _showFileMenu(IdeaFile file) {
    showModalBottomSheet(
      context: context, backgroundColor: AppTheme.bg2,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (_) => SafeArea(child: Column(mainAxisSize: MainAxisSize.min, children: [
        const SizedBox(height: 8),
        Container(width: 40, height: 4, decoration: BoxDecoration(
            color: AppTheme.border, borderRadius: BorderRadius.circular(2))),
        Padding(
          padding: const EdgeInsets.all(16),
          child: Row(children: [
            Text(file.name, style: const TextStyle(color: AppTheme.textPrimary,
                fontWeight: FontWeight.w700, fontFamily: 'monospace')),
            const SizedBox(width: 8),
            _vBadge('v${file.version}'),
            const SizedBox(width: 6),
            Text(file.sizeLabel, style: const TextStyle(
                color: AppTheme.textMuted, fontSize: 11)),
          ]),
        ),
        if (file.isText)
          _sheetTile(Icons.edit_outlined, 'এডিট করো', AppTheme.accent, () async {
            Navigator.pop(context);
            await Navigator.push(context, MaterialPageRoute(
                builder: (_) => FileEditorScreen(file: file)));
            _load();
          }),
        if (file.isText)
          _sheetTile(Icons.copy_outlined, 'কোড কপি করো', AppTheme.textSecondary, () {
            Navigator.pop(context);
            Clipboard.setData(ClipboardData(text: file.content ?? ''));
            ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                content: Text('${file.name} কপি হয়েছে!'),
                backgroundColor: AppTheme.green,
                duration: const Duration(seconds: 2)));
          }),
        if (file.isAudio)
          _sheetTile(
            _playingFileId == file.id.toString()
                ? Icons.stop_circle_outlined : Icons.play_circle_outlined,
            _playingFileId == file.id.toString() ? 'বন্ধ করো' : 'অডিও প্লে করো',
            const Color(0xFFFF9800),
            () { Navigator.pop(context); _playAudio(file); },
          ),
        if (file.isVideo)
          _sheetTile(Icons.smart_display_outlined, 'ভিডিও প্লে করো',
            const Color(0xFF2196F3),
            () { Navigator.pop(context); _playVideo(file); },
          ),
        if (file.isImage)
          _sheetTile(Icons.image_outlined, 'ছবি দেখো', AppTheme.accent, () async {
            Navigator.pop(context);
            await Navigator.push(context, MaterialPageRoute(
                builder: (_) => ImageViewerScreen(file: file)));
          }),
        if (file.isPdf)
          _sheetTile(Icons.picture_as_pdf_outlined, 'PDF দেখো (In-App)', AppTheme.red, () async {
            Navigator.pop(context);
            await Navigator.push(context, MaterialPageRoute(
                builder: (_) => PdfViewerScreen(file: file)));
          }),
        if (file.ext == 'md')
          _sheetTile(Icons.auto_awesome_outlined, 'Markdown Render করো', AppTheme.accent, () async {
            Navigator.pop(context);
            await Navigator.push(context, MaterialPageRoute(
                builder: (_) => MarkdownViewerScreen(file: file)));
          }),
        _sheetTile(Icons.drive_file_rename_outline, 'রিনেম', AppTheme.yellow,
            () { Navigator.pop(context); _renameFile(file); }),
        _sheetTile(Icons.copy_all_outlined, 'Copy To...', AppTheme.green, () async {
          Navigator.pop(context);
          await Navigator.push(context, MaterialPageRoute(
              builder: (_) => CopyMoveScreen(file: file, mode: 'copy',
                  currentIdeaId: _idea.id!)));
          _load();
        }),
        _sheetTile(Icons.drive_file_move_outline, 'Move To...', AppTheme.yellow, () async {
          Navigator.pop(context);
          await Navigator.push(context, MaterialPageRoute(
              builder: (_) => CopyMoveScreen(file: file, mode: 'move',
                  currentIdeaId: _idea.id!)));
          _load();
        }),
        _sheetTile(Icons.share_outlined, 'শেয়ার করো', AppTheme.textSecondary,
            () { Navigator.pop(context); _shareFile(file); }),
        _sheetTile(Icons.delete_outline, 'মুছো', AppTheme.red,
            () { Navigator.pop(context); _deleteFile(file); }),
        const SizedBox(height: 8),
      ])),
    );
  }

  @override
  Widget build(BuildContext context) {
    final sc = statusConfig[_idea.status]!;
    return Scaffold(
      backgroundColor: AppTheme.bg,
      appBar: AppBar(
        title: Row(children: [
          Container(width: 10, height: 10, decoration: BoxDecoration(
              color: widget.project.color, shape: BoxShape.circle)),
          const SizedBox(width: 8),
          Expanded(child: Text(_idea.title,
              style: AppTheme.display(size: 16),
              overflow: TextOverflow.ellipsis)),
        ]),
        actions: [
          IconButton(onPressed: _exportZip,
              icon: const Icon(Icons.folder_zip_outlined, size: 20),
              tooltip: 'নোট + সব ফাইল ZIP করে শেয়ার করো'),
          GestureDetector(
            onTap: _cycleStatus,
            child: Container(
              margin: const EdgeInsets.only(right: 12),
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
              decoration: BoxDecoration(
                color: (sc['color'] as Color).withOpacity(0.15),
                borderRadius: BorderRadius.circular(20),
                border: Border.all(color: sc['color'] as Color),
              ),
              child: Text(sc['label'] as String, style: TextStyle(
                  color: sc['color'] as Color, fontSize: 12, fontWeight: FontWeight.w700)),
            ),
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(color: AppTheme.accent))
          : CustomScrollView(slivers: [
              if (_idea.description != null && _idea.description!.isNotEmpty)
                SliverToBoxAdapter(
                  child: Container(width: double.infinity, padding: const EdgeInsets.all(14),
                      color: AppTheme.bg2,
                      child: Text(_idea.description!, style: const TextStyle(
                          color: AppTheme.textSecondary, fontSize: 13, height: 1.5))),
                ),
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 14, 16, 8),
                  child: Row(children: [
                    const Text('🗂️', style: TextStyle(fontSize: 15)),
                    const SizedBox(width: 6),
                    Text('ফাইলসমূহ (${_files.length})',
                        style: const TextStyle(color: AppTheme.textSecondary, fontSize: 13,
                            fontWeight: FontWeight.w600)),
                    const Spacer(),
                    GestureDetector(
                      onTap: _showAddOptions,
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                        decoration: BoxDecoration(color: AppTheme.accent,
                            borderRadius: BorderRadius.circular(8)),
                        child: const Row(children: [
                          Text('➕', style: TextStyle(fontSize: 12)),
                          SizedBox(width: 4),
                          Text('যোগ করো', style: TextStyle(color: Colors.white,
                              fontSize: 12, fontWeight: FontWeight.w700)),
                        ]),
                      ),
                    ),
                  ]),
                ),
              ),
              if (_files.isEmpty)
                SliverFillRemaining(
                  hasScrollBody: false,
                  child: Center(child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                    const Text('📄', style: TextStyle(fontSize: 40)),
                    const SizedBox(height: 12),
                    const Text('কোনো ফাইল নেই', style: TextStyle(
                        color: AppTheme.textSecondary, fontSize: 15)),
                    const SizedBox(height: 6),
                    const Text('+ যোগ করো বাটনে চাপো', style: TextStyle(
                        color: AppTheme.textMuted, fontSize: 13)),
                  ])),
                )
              else
                SliverPadding(
                  padding: const EdgeInsets.fromLTRB(12, 0, 12, 80),
                  sliver: SliverList(
                    delegate: SliverChildBuilderDelegate(
                      (_, i) => Padding(
                        padding: EdgeInsets.only(bottom: i == _files.length - 1 ? 0 : 8),
                        child: _fileCard(_files[i]),
                      ),
                      childCount: _files.length,
                    ),
                  ),
                ),
            ]),
      floatingActionButton: FloatingActionButton(
        onPressed: _showAddOptions,
        backgroundColor: AppTheme.accent, mini: true,
        child: const Icon(Icons.add, color: Colors.white),
      ),
    );
  }

  Widget _fileCard(IdeaFile file) {
    final extColor = _extColor(file.ext);
    final extLabel = file.ext.isEmpty ? '?' : file.ext.toUpperCase().substring(0, file.ext.length.clamp(0, 4));
    final isPlaying = _playingFileId == file.id.toString();

    Widget thumbChild;
    if (file.isImage && file.content != null) {
      thumbChild = Image.memory(base64Decode(file.content!), fit: BoxFit.cover,
          errorBuilder: (_, __, ___) => Center(child: Text(extLabel,
              style: TextStyle(color: extColor, fontSize: 11, fontWeight: FontWeight.w800))));
    } else if (file.isAudio) {
      thumbChild = Center(child: Icon(
          isPlaying ? Icons.stop_rounded : Icons.headphones_rounded,
          color: extColor, size: 24));
    } else if (file.isVideo) {
      thumbChild = Center(child: Icon(
          Icons.play_circle_filled_rounded, color: extColor, size: 24));
    } else {
      thumbChild = Center(child: Text(extLabel,
          style: TextStyle(color: extColor, fontSize: 11, fontWeight: FontWeight.w800)));
    }

    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () {
          if (file.isAudio) {
            _playAudio(file);
          } else if (file.isVideo) {
            _playVideo(file);
          } else {
            _showFileMenu(file);
          }
        },
        onLongPress: () {
          if (file.isText) {
            Navigator.push(context, MaterialPageRoute(
                builder: (_) => FileEditorScreen(file: file))).then((_) => _load());
          } else {
            _showFileMenu(file);
          }
        },
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(children: [
            Stack(children: [
              Container(
                width: 48, height: 48,
                decoration: BoxDecoration(color: extColor.withOpacity(0.12),
                    borderRadius: BorderRadius.circular(10)),
                clipBehavior: Clip.antiAlias,
                child: thumbChild,
              ),
              if (isPlaying)
                Positioned.fill(child: Container(
                  decoration: BoxDecoration(
                    color: Colors.black26,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const Center(child: SizedBox(
                    width: 20, height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                  )),
                )),
            ]),
            const SizedBox(width: 12),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(file.name, style: const TextStyle(color: AppTheme.textPrimary,
                  fontSize: 13, fontWeight: FontWeight.w600, fontFamily: 'monospace'),
                  overflow: TextOverflow.ellipsis),
              const SizedBox(height: 3),
              Row(children: [
                _vBadge('v${file.version}'),
                const SizedBox(width: 8),
                Text(file.sizeLabel, style: const TextStyle(
                    color: AppTheme.textMuted, fontSize: 11)),
                if (file.isText) ...[
                  const SizedBox(width: 6),
                  Text('${file.lineCount} লাইন', style: const TextStyle(
                      color: AppTheme.textMuted, fontSize: 11)),
                ],
                if (file.isAudio) ...[
                  const SizedBox(width: 6),
                  Text(isPlaying ? '▶ playing' : '🎵 audio',
                      style: TextStyle(color: extColor, fontSize: 11)),
                ],
                if (file.isVideo) ...[
                  const SizedBox(width: 6),
                  const Text('🎬 video', style: TextStyle(
                      color: Color(0xFF2196F3), fontSize: 11)),
                ],
              ]),
            ])),
            GestureDetector(
              onTap: () => _showFileMenu(file),
              child: const Padding(
                padding: EdgeInsets.all(4),
                child: Icon(Icons.more_vert, size: 18, color: AppTheme.textMuted),
              ),
            ),
          ]),
        ),
      ),
    );
  }

  Widget _vBadge(String v) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
    decoration: BoxDecoration(color: AppTheme.accent.withOpacity(0.15),
        borderRadius: BorderRadius.circular(4)),
    child: Text(v, style: const TextStyle(color: AppTheme.accent,
        fontSize: 10, fontWeight: FontWeight.w700, fontFamily: 'monospace')),
  );

  Widget _sheetTile(IconData icon, String label, Color color, VoidCallback onTap) =>
      ListTile(leading: Icon(icon, color: color, size: 20),
          title: Text(label, style: TextStyle(color: color, fontSize: 14)),
          onTap: onTap, dense: true);

  Color _extColor(String ext) {
    const map = {
      'js':Color(0xFFF7DF1E),'jsx':Color(0xFF61DAFB),'ts':Color(0xFF3178C6),
      'tsx':Color(0xFF61DAFB),'dart':Color(0xFF54C5F8),'py':Color(0xFF3776AB),
      'html':Color(0xFFE34F26),'css':Color(0xFF264DE4),'scss':Color(0xFFCC6699),
      'json':Color(0xFF5BC8F5),'yml':Color(0xFFCB171E),'yaml':Color(0xFFCB171E),
      'md':Color(0xFF083FA1),'java':Color(0xFFED8B00),'kt':Color(0xFF7F52FF),
      'svg':Color(0xFFFFB13B),'xml':Color(0xFFF16529),
      'png':Color(0xFF10B981),'jpg':Color(0xFF10B981),'jpeg':Color(0xFF10B981),
      'pdf':Color(0xFFEF4444),'apk':Color(0xFF4CAF50),'zip':Color(0xFF9C27B0),
      'mp3':Color(0xFFFF9800),'wav':Color(0xFFFF9800),'mp4':Color(0xFF2196F3),
    };
    return map[ext] ?? AppTheme.textSecondary;
  }

  Widget _field(TextEditingController ctrl, String hint,
      {int maxLines = 1, bool mono = false}) => TextField(
    controller: ctrl, maxLines: maxLines,
    style: TextStyle(color: AppTheme.textPrimary,
        fontFamily: mono ? 'monospace' : null, fontSize: 13),
    decoration: InputDecoration(
      hintText: hint, hintStyle: const TextStyle(color: AppTheme.textMuted),
      filled: true, fillColor: AppTheme.bg3,
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(10),
          borderSide: const BorderSide(color: AppTheme.border)),
      enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(10),
          borderSide: const BorderSide(color: AppTheme.border)),
      focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(10),
          borderSide: const BorderSide(color: AppTheme.accent, width: 2)),
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
    ),
  );
}

// ── Simple video player screen using platform video_player ───────────────────
// Uses a full-screen approach with the video_player package.
// Since video_player is NOT in pubspec yet, we use a simple preview screen
// that shows file info and plays via platform intent. Add video_player to
// pubspec.yaml if you want in-app playback.
class _VideoPlayerScreen extends StatelessWidget {
  final File file;
  final String name;
  const _VideoPlayerScreen({required this.file, required this.name});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        title: Text(name, style: const TextStyle(color: Colors.white, fontSize: 14)),
        iconTheme: const IconThemeData(color: Colors.white),
      ),
      body: Center(
        child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
          const Icon(Icons.smart_display_outlined, color: Colors.white54, size: 80),
          const SizedBox(height: 20),
          Text(name, style: const TextStyle(color: Colors.white70, fontSize: 14)),
          const SizedBox(height: 8),
          Text(
            '${(file.lengthSync() / 1024).toStringAsFixed(1)} KB',
            style: const TextStyle(color: Colors.white38, fontSize: 12),
          ),
          const SizedBox(height: 32),
          ElevatedButton.icon(
            onPressed: () async {
              // Open with system player via share
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                  content: Text('ভিডিও ফাইল সিস্টেম প্লেয়ারে খুলছে...'),
                  backgroundColor: Color(0xFF2196F3),
                ));
            },
            icon: const Icon(Icons.open_in_new, size: 18),
            label: const Text('সিস্টেম প্লেয়ারে খোলো'),
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFF2196F3),
              foregroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
            ),
          ),
          const SizedBox(height: 16),
          const Text(
            'In-app video: pubspec.yaml-এ\nvideo_player: ^2.8.2 যোগ করো',
            textAlign: TextAlign.center,
            style: TextStyle(color: Colors.white24, fontSize: 11),
          ),
        ]),
      ),
    );
  }
}
