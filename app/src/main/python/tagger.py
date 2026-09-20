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
    def __init__(self, client_id: str = None, client_secret: str = None):
        self.sp = None
        if HAS_SPOTIPY and client_id and client_secret:
            try:
                auth = SpotifyClientCredentials(client_id=client_id, client_secret=client_secret)
                self.sp = spotipy.Spotify(client_credentials_manager=auth)
            except Exception as e:
                print(f"[!] Spotify auth failed: {e}")

    def fetch_spotify_data(self, query: str) -> dict:
        if not self.sp:
            return {"title": query, "artist": "Unknown Artist", "album": "Single", "genre": "Music", "cover_url": None}

        try:
            results = self.sp.search(q=query, type="track", limit=1)
            items = results.get("tracks", {}).get("items", [])
            if not items:
                return {"title": query, "artist": "Unknown Artist", "album": "Single", "genre": "Music", "cover_url": None}

            track = items[0]
            artist_id = track["artists"][0]["id"]
            artist_info = self.sp.artist(artist_id)
            genres = artist_info.get("genres", [])

            return {
                "title": track["name"],
                "artist": ", ".join([a["name"] for a in track["artists"]]),
                "album": track["album"]["name"],
                "genre": genres[0].title() if genres else "Pop",
                "cover_url": track["album"]["images"][0]["url"] if track["album"]["images"] else None
            }
        except Exception as e:
            print(f"[-] Spotify search error: {e}")
            return {"title": query, "artist": "Unknown Artist", "album": "Single", "genre": "Music", "cover_url": None}

    def fetch_synced_lyrics(self, query: str, base_file_path: str) -> str:
        try:
            lrc_content = syncedlyrics.search(query)
            if lrc_content:
                lrc_path = os.path.splitext(base_file_path)[0] + ".lrc"
                with open(lrc_path, "w", encoding="utf-8") as f:
                    f.write(lrc_content)
                return lrc_content
        except Exception as e:
            print(f"[-] Synced lyrics lookup failed: {e}")
        return None

    def embed_mp3(self, file_path: str, meta: dict, image_data: bytes, lyrics: str):
        try:
            audio = MP3(file_path, ID3=ID3)
        except ID3NoHeaderError:
            audio = MP3(file_path)
            audio.add_tags()

        if meta.get("title"):
            audio.tags.add(TIT2(encoding=3, text=meta["title"]))
        if meta.get("artist"):
            audio.tags.add(TPE1(encoding=3, text=meta["artist"]))
        if meta.get("album"):
            audio.tags.add(TALB(encoding=3, text=meta["album"]))
        if meta.get("genre"):
            audio.tags.add(TCON(encoding=3, text=meta["genre"]))

        if image_data:
            audio.tags.add(APIC(encoding=3, mime="image/jpeg", type=3, desc="Cover", data=image_data))

        if lyrics:
            audio.tags.add(USLT(encoding=3, lang="eng", desc="Lyrics", text=lyrics))

        audio.save()

    def embed_mp4(self, file_path: str, meta: dict, image_data: bytes, lyrics: str):
        audio = MP4(file_path)
        if meta.get("title"):
            audio["\xa9nam"] = meta["title"]
        if meta.get("artist"):
            audio["\xa9ART"] = meta["artist"]
        if meta.get("album"):
            audio["\xa9alb"] = meta["album"]
        if meta.get("genre"):
            audio["\xa9gen"] = meta["genre"]
        if image_data:
            audio["covr"] = [MP4Cover(image_data, imageformat=MP4Cover.FORMAT_JPEG)]
        if lyrics:
            audio["\xa9lyr"] = lyrics
        audio.save()

    def process(self, file_path: str, search_query: str):
        meta = self.fetch_spotify_data(search_query)
        
        image_data = None
        if meta.get("cover_url"):
            res = requests.get(meta["cover_url"], timeout=10)
            if res.status_code == 200:
                image_data = res.content

        lyrics_text = self.fetch_synced_lyrics(f"{meta['artist']} {meta['title']}", file_path)

        ext = os.path.splitext(file_path)[1].lower()
        if ext == ".mp3":
            self.embed_mp3(file_path, meta, image_data, lyrics_text)
        elif ext in [".mp4", ".m4a"]:
            self.embed_mp4(file_path, meta, image_data, lyrics_text)
      
# ... (Keep all your existing tagger.py code above this) ...

def scan_and_update_library(directory_path, client_id=None, client_secret=None, progress_callback=None):
    import glob
    
    engine = MusicMetadataEngine(client_id, client_secret)
    if not os.path.exists(directory_path):
        return f"Folder not found: {directory_path}"
        
    supported_exts = ['.mp3', '.m4a', '.mp4']
    files = []
    for ext in supported_exts:
        files.extend(glob.glob(os.path.join(directory_path, f"*{ext}")))
        
    if not files:
        return "No music files found in this folder."

    updated_count = 0
    for i, file_path in enumerate(files):
        filename = os.path.basename(file_path)
        clean_name = os.path.splitext(filename)[0]
        
        if progress_callback:
            progress_callback.onProgress(f"Checking {i+1}/{len(files)}: {clean_name[:15]}...")
            
        needs_genre = False
        ext = os.path.splitext(file_path)[1].lower()
        
        try:
            if ext == '.mp3':
                audio = MP3(file_path, ID3=ID3)
                if not audio.tags or 'TCON' not in audio.tags:
                    needs_genre = True
            elif ext in ['.mp4', '.m4a']:
                audio = MP4(file_path)
                if '\xa9gen' not in audio:
                    needs_genre = True
        except:
            needs_genre = True # If tags are corrupted/missing, force update

        if needs_genre:
            if progress_callback:
                progress_callback.onProgress(f"Tagging: {clean_name[:15]}...")
            engine.process(file_path, clean_name)
            updated_count += 1
            
    return f"Scan Complete! Updated {updated_count} missing genres."
        
