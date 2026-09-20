import os
import requests
import syncedlyrics
import mutagen
from mutagen.mp3 import MP3
from mutagen.id3 import ID3, TIT2, TPE1, TALB, TCON, APIC, USLT, ID3NoHeaderError
from mutagen.mp4 import MP4, MP4Cover

try:
    import spotipy
    from spotipy.oauth2 import SpotifyClientCredentials
    HAS_SPOTIPY = True
except ImportError:
    HAS_SPOTIPY = False

class MusicMetadataEngine:
    def __init__(self, client_id=None, client_secret=None):
        self.sp = None
        if HAS_SPOTIPY and client_id and client_secret:
            try:
                auth = SpotifyClientCredentials(client_id=client_id, client_secret=client_secret)
                self.sp = spotipy.Spotify(client_credentials_manager=auth)
            except Exception:
                pass

    def fetch_spotify_data(self, query: str) -> dict:
        if not self.sp:
            return {"title": query, "artist": "Unknown", "album": "Unknown", "genre": "Music", "cover_url": None}
        try:
            results = self.sp.search(q=query, type="track", limit=1)
            items = results.get("tracks", {}).get("items", [])
            if not items:
                return {"title": query, "artist": "Unknown", "album": "Unknown", "genre": "Music", "cover_url": None}
            track = items[0]
            artist_id = track["artists"][0]["id"]
            genres = self.sp.artist(artist_id).get("genres", [])
            return {
                "title": track["name"],
                "artist": ", ".join([a["name"] for a in track["artists"]]),
                "album": track["album"]["name"],
                "genre": genres[0].title() if genres else "Pop",
                "cover_url": track["album"]["images"][0]["url"] if track["album"]["images"] else None
            }
        except Exception:
            return {"title": query, "artist": "Unknown", "album": "Unknown", "genre": "Music", "cover_url": None}

    def fetch_synced_lyrics(self, query: str, base_file_path: str) -> str:
        try:
            lrc = syncedlyrics.search(query)
            if lrc:
                with open(os.path.splitext(base_file_path)[0] + ".lrc", "w", encoding="utf-8") as f:
                    f.write(lrc)
                return lrc
        except Exception:
            pass
        return None

    def embed_mp3(self, file_path: str, meta: dict, image_data: bytes, lyrics: str):
        try:
            audio = MP3(file_path, ID3=ID3)
        except ID3NoHeaderError:
            audio = MP3(file_path)
            audio.add_tags()
        if meta.get("title"): audio.tags.add(TIT2(encoding=3, text=meta["title"]))
        if meta.get("artist"): audio.tags.add(TPE1(encoding=3, text=meta["artist"]))
        if meta.get("genre"): audio.tags.add(TCON(encoding=3, text=meta["genre"]))
        if image_data: audio.tags.add(APIC(encoding=3, mime="image/jpeg", type=3, desc="Cover", data=image_data))
        if lyrics: audio.tags.add(USLT(encoding=3, lang="eng", desc="Lyrics", text=lyrics))
        audio.save()

    def embed_mp4(self, file_path: str, meta: dict, image_data: bytes, lyrics: str):
        audio = MP4(file_path)
        if meta.get("title"): audio["\xa9nam"] = meta["title"]
        if meta.get("artist"): audio["\xa9ART"] = meta["artist"]
        if meta.get("genre"): audio["\xa9gen"] = meta["genre"]
        if image_data: audio["covr"] = [MP4Cover(image_data, imageformat=MP4Cover.FORMAT_JPEG)]
        if lyrics: audio["\xa9lyr"] = lyrics
        audio.save()

    def process(self, file_path: str, search_query: str):
        meta = self.fetch_spotify_data(search_query)
        img = None
        if meta.get("cover_url"):
            try:
                res = requests.get(meta["cover_url"], timeout=10)
                if res.status_code == 200: img = res.content
            except Exception:
                pass
        lyrics = self.fetch_synced_lyrics(f"{meta['artist']} {meta['title']}", file_path)
        ext = os.path.splitext(file_path)[1].lower()
        if ext == ".mp3": self.embed_mp3(file_path, meta, img, lyrics)
        elif ext in [".mp4", ".m4a"]: self.embed_mp4(file_path, meta, img, lyrics)

def scan_and_update_library(path, scan_entire_device=False, client_id=None, client_secret=None, progress_callback=None):
    engine = MusicMetadataEngine(client_id, client_secret)
    supported_exts = ('.mp3', '.m4a', '.mp4')
    files_to_process = []

    if scan_entire_device:
        for root, _, filenames in os.walk("/storage/emulated/0"):
            for f in filenames:
                if f.lower().endswith(supported_exts):
                    files_to_process.append(os.path.join(root, f))
    else:
        if not os.path.exists(path): return f"Folder not found: {path}"
        for f in os.listdir(path):
            if f.lower().endswith(supported_exts):
                files_to_process.append(os.path.join(path, f))

    if not files_to_process: return "No audio files found."
    
    updated_count = 0
    total = len(files_to_process)

    for i, file_path in enumerate(files_to_process):
        clean_name = os.path.splitext(os.path.basename(file_path))[0]
        if progress_callback: progress_callback.onProgress(f"({i+1}/{total}) {clean_name[:15]}...")

        try:
            # Check if file is valid and needs a genre
            needs_genre = False
            ext = os.path.splitext(file_path)[1].lower()
            if ext == '.mp3':
                audio = MP3(file_path, ID3=ID3)
                if not audio.tags or 'TCON' not in audio.tags: needs_genre = True
            elif ext in ['.mp4', '.m4a']:
                audio = MP4(file_path)
                if '\xa9gen' not in audio: needs_genre = True

            # Process valid files
            if needs_genre:
                engine.process(file_path, clean_name)
                updated_count += 1
        except Exception:
            # Silently skip corrupted or fake media files
            continue

    return f"Done! Scanned {total} files, updated {updated_count} missing genres."
                                                  
