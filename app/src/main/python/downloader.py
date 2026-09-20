import os
import glob
import yt_dlp
from tagger import MusicMetadataEngine

def run_download_and_process(url, choice, search_query, client_id=None, client_secret=None, progress_callback=None):
    output_folder = "/storage/emulated/0/Download/MyConverter"
    os.makedirs(output_folder, exist_ok=True)

    def handle_progress(d):
        if d['status'] == 'downloading' and progress_callback:
            percent_str = d.get('_percent_str', '0%').strip()
            progress_callback.onProgress(percent_str)

    # Use universal 'best' format matching to avoid YouTube client restrictions
    ydl_opts = {
        'format': 'best/ba/b',
        'outtmpl': os.path.join(output_folder, '%(title)s.%(ext)s'),
        'quiet': False,
        'noplaylist': True,
        'rm_cachedir': True,
        'progress_hooks': [handle_progress],
        'extractor_args': {
            'youtube': {
                'player_client': ['android', 'web']
            }
        }
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=True)
            filename = ydl.prepare_filename(info)

        base_path = os.path.splitext(filename)[0]
        matching_files = glob.glob(f"{glob.escape(base_path)}.*")
        
        media_files = [f for f in matching_files if not f.endswith('.lrc') and not f.endswith('.part')]
        final_file = media_files[0] if media_files else filename

        final_query = search_query if search_query and search_query.strip() else info.get('title', '')
        
        engine = MusicMetadataEngine(client_id, client_secret)
        engine.process(final_file, final_query)

        return "Success"
    except Exception as e:
        return f"ERROR: {str(e)}"
            
