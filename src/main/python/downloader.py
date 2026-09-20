import os
import yt_dlp
from tagger import MusicMetadataEngine

def run_download_and_process(url, choice, search_query, client_id=None, client_secret=None, progress_callback=None):
    output_folder = "/storage/emulated/0/Download/MyConverter"
    os.makedirs(output_folder, exist_ok=True)

    def length_filter(info_dict, *, incomplete):
        duration = info_dict.get('duration')
        if duration and duration < 2700:
            return "Skipping: Video is too short (< 45 min)."
        return None

    def handle_progress(d):
        if d['status'] == 'downloading' and progress_callback:
            percent_str = d.get('_percent_str', '0%').strip()
            progress_callback.onProgress(percent_str)

    ydl_opts = {
        'outtmpl': os.path.join(output_folder, '%(title)s.%(ext)s'),
        'quiet': False,
        'noplaylist': True,
        'rm_cachedir': True,
        'match_filter': length_filter,
        'progress_hooks': [handle_progress],
    }

    if choice == 1:
        ydl_opts.update({
            'format': 'bestaudio/best',
            'postprocessors': [{
                'key': 'FFmpegExtractAudio',
                'preferredcodec': 'mp3',
                'preferredquality': '192',
            }],
        })
    else:
        ydl_opts.update({
            'format': 'bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best',
            'merge_output_format': 'mp4',
        })

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=True)
            filename = ydl.prepare_filename(info)
            if choice == 1:
                filename = os.path.splitext(filename)[0] + ".mp3"

        # Auto-detect search term from video title if not explicitly provided
        final_query = search_query if search_query and search_query.strip() else info.get('title', '')
        
        # Run Spotify tagging + Synced Lyrics fetching
        engine = MusicMetadataEngine(client_id, client_secret)
        engine.process(filename, final_query)

        return "Success"
    except Exception as e:
        return str(e)
          
