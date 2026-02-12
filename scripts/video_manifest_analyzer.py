#!/usr/bin/env python3

import json
import os
import sys
import time
from urllib.parse import urlparse
import requests
from pathlib import Path
import warnings
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
from typing import Dict, List, Optional, Tuple
import pickle

# Suppress InsecureRequestWarning and other urllib3 warnings
warnings.filterwarnings("ignore", message="Unverified HTTPS request is being made to host")
warnings.filterwarnings("ignore", message="InsecureRequestWarning")
warnings.filterwarnings("ignore", message="certificate verify failed")

# Configuration
MAX_WORKERS = 20  # Number of concurrent requests
MAX_RETRIES = 3   # Number of retry attempts
RETRY_DELAY = 1   # Initial retry delay in seconds
CACHE_FILE = '.video_size_cache.pkl'


def get_video_size(url: str, retries: int = MAX_RETRIES) -> Optional[int]:
    """
    Get the size of a video file from its URL using HTTP HEAD request.
    Includes retry logic with exponential backoff.

    Args:
        url (str): URL of the video file
        retries (int): Number of retry attempts

    Returns:
        int: Size of the video in bytes, or None if unable to determine
    """
    for attempt in range(retries):
        try:
            # Send a HEAD request to get content length
            # Disable SSL verification due to certificate issues with apple.com domains
            response = requests.head(url, allow_redirects=True, timeout=10, verify=False)

            if 'Content-Length' in response.headers:
                return int(response.headers['Content-Length'])

            # If HEAD doesn't work, try GET with range header
            response = requests.get(url, headers={'Range': 'bytes=0-0'}, timeout=10, verify=False)
            if 'Content-Range' in response.headers:
                content_range = response.headers['Content-Range']
                # Format: bytes 0-0/total_size
                total_size = content_range.split('/')[-1]
                return int(total_size)

            return None
            
        except requests.exceptions.Timeout:
            if attempt < retries - 1:
                delay = RETRY_DELAY * (2 ** attempt)  # Exponential backoff
                time.sleep(delay)
            else:
                return None
                
        except Exception as e:
            if attempt < retries - 1:
                delay = RETRY_DELAY * (2 ** attempt)
                time.sleep(delay)
            else:
                return None
    
    return None


def load_cache() -> Dict[str, int]:
    """
    Load cached video sizes from disk.
    
    Returns:
        dict: Dictionary mapping URL hashes to sizes
    """
    if os.path.exists(CACHE_FILE):
        try:
            with open(CACHE_FILE, 'rb') as f:
                return pickle.load(f)
        except Exception:
            return {}
    return {}


def save_cache(cache: Dict[str, int]):
    """
    Save video sizes to cache file.
    
    Args:
        cache (dict): Dictionary mapping URL hashes to sizes
    """
    try:
        with open(CACHE_FILE, 'wb') as f:
            pickle.dump(cache, f)
    except Exception as e:
        print(f"\nWarning: Could not save cache: {e}")


def get_url_hash(url: str) -> str:
    """
    Generate a hash for a URL to use as cache key.
    
    Args:
        url (str): URL to hash
        
    Returns:
        str: Hash of the URL
    """
    return hashlib.md5(url.encode()).hexdigest()


def load_manifest(file_path: Path) -> Optional[Dict]:
    """
    Load a video manifest file with error handling.
    
    Args:
        file_path (Path): Path to the manifest file
        
    Returns:
        dict: Parsed manifest data, or None if error
    """
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
            
        # Validate structure
        if not isinstance(data, dict):
            print(f"  Error: Manifest is not a dictionary")
            return None
            
        if 'assets' not in data:
            print(f"  Error: Manifest missing 'assets' key")
            return None
            
        if not isinstance(data['assets'], list):
            print(f"  Error: 'assets' is not a list")
            return None
            
        return data
        
    except json.JSONDecodeError as e:
        print(f"  Error: Invalid JSON - {e}")
        return None
    except Exception as e:
        print(f"  Error reading file: {e}")
        return None


def extract_video_urls(manifest_data: Dict, platform_name: str) -> List[Dict]:
    """
    Extract all video URLs from a manifest, along with their properties.
    
    Args:
        manifest_data (dict): Parsed manifest data
        platform_name (str): Name of the platform
        
    Returns:
        list: List of dictionaries containing video info
    """
    videos = []
    
    for asset in manifest_data.get('assets', []):
        if not isinstance(asset, dict):
            continue
            
        # Look for URLs with resolution and format indicators
        for key, url in asset.items():
            if key.startswith('url-') and isinstance(url, str) and url.startswith(('http://', 'https://')):
                # Parse resolution and format from the key
                # Example: url-4K-HDR, url-1080-SDR, url-1080-H264
                parts = key.replace('url-', '').split('-')
                
                resolution = 'unknown'
                format_type = 'unknown'
                
                if len(parts) >= 1:
                    resolution = parts[0]  # 4K, 1080, etc.
                    
                if len(parts) >= 2:
                    format_type = '-'.join(parts[1:])  # HDR, SDR, H264, etc.
                
                video_info = {
                    'url': url,
                    'platform': platform_name,
                    'resolution': resolution,
                    'format': format_type,
                    'asset_id': asset.get('id', ''),
                    'title': asset.get('accessibilityLabel', asset.get('title', '')),
                    'size_bytes': None  # Will be filled later
                }
                
                videos.append(video_info)
    
    return videos


def fetch_video_sizes_parallel(videos: List[Dict], cache: Dict[str, int]) -> Tuple[int, int]:
    """
    Fetch video sizes in parallel using ThreadPoolExecutor.
    
    Args:
        videos (list): List of video info dictionaries
        cache (dict): Cache of previously fetched sizes
        
    Returns:
        tuple: (successful_count, failed_count)
    """
    successful = 0
    failed = 0
    
    # Separate videos into cached and uncached
    uncached_videos = []
    for video in videos:
        url_hash = get_url_hash(video['url'])
        if url_hash in cache:
            video['size_bytes'] = cache[url_hash]
            successful += 1
        else:
            uncached_videos.append(video)
    
    if uncached_videos:
        print(f"  Fetching sizes for {len(uncached_videos)} uncached videos...")
        print(f"  Using {MAX_WORKERS} parallel workers...")
        
        # Create a thread pool and fetch sizes in parallel
        with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
            # Submit all tasks
            future_to_video = {
                executor.submit(get_video_size, video['url']): video 
                for video in uncached_videos
            }
            
            # Process completed tasks
            completed = 0
            total = len(uncached_videos)
            
            for future in as_completed(future_to_video):
                video = future_to_video[future]
                completed += 1
                
                try:
                    size = future.result()
                    video['size_bytes'] = size
                    
                    if size is not None:
                        successful += 1
                        # Update cache
                        url_hash = get_url_hash(video['url'])
                        cache[url_hash] = size
                    else:
                        failed += 1
                        
                except Exception as e:
                    failed += 1
                    video['size_bytes'] = None
                
                # Progress update
                progress_pct = (completed / total) * 100
                print(f"    Progress: {completed}/{total} ({progress_pct:.1f}%) - Success: {successful}, Failed: {failed}", end='\r')
        
        print()  # New line after progress
    
    return successful, failed


def calculate_totals(videos: List[Dict], platform_filter: Optional[str] = None, 
                    resolution_filter: Optional[str] = None, 
                    format_filter: Optional[str] = None) -> Dict:
    """
    Calculate total sizes grouped by platform, resolution and format.
    
    Args:
        videos (list): List of video info dictionaries
        platform_filter (str): Optional platform to filter by
        resolution_filter (str): Optional resolution to filter by
        format_filter (str): Optional format to filter by (e.g., HDR, SDR, H264)
        
    Returns:
        dict: Aggregated totals
    """
    totals = {}
    
    for video in videos:
        # Apply filters
        if platform_filter and video['platform'].lower() != platform_filter.lower():
            continue
            
        if resolution_filter and video['resolution'].lower() != resolution_filter.lower():
            continue
            
        if format_filter and video['format'].lower() != format_filter.lower():
            continue
        
        platform = video['platform']
        resolution = video['resolution']
        format_type = video['format']
        
        # Initialize nested dictionary structure
        if platform not in totals:
            totals[platform] = {}
        
        # Create a combined key for resolution and format
        res_format_key = f"{resolution}-{format_type}"
        
        if res_format_key not in totals[platform]:
            totals[platform][res_format_key] = {
                'count': 0,
                'total_size_bytes': 0,
                'videos': [],
                'resolution': resolution,
                'format': format_type,
                'failed_count': 0
            }
        
        # Add video info
        totals[platform][res_format_key]['count'] += 1
        if video['size_bytes']:
            totals[platform][res_format_key]['total_size_bytes'] += video['size_bytes']
        else:
            totals[platform][res_format_key]['failed_count'] += 1
        totals[platform][res_format_key]['videos'].append(video)
    
    return totals


def format_size(size_bytes: Optional[int]) -> str:
    """
    Format size in bytes to human-readable format.
    
    Args:
        size_bytes (int): Size in bytes
        
    Returns:
        str: Human-readable size string
    """
    if size_bytes is None or size_bytes == 0:
        return "Unknown"
    
    for unit in ['B', 'KB', 'MB', 'GB', 'TB']:
        if size_bytes < 1024.0:
            return f"{size_bytes:.2f} {unit}"
        size_bytes /= 1024.0
    
    return f"{size_bytes:.2f} PB"


def print_summary(totals: Dict, title: str = "VIDEO SIZE SUMMARY BY PLATFORM, RESOLUTION AND FORMAT"):
    """
    Print a formatted summary of video totals.
    
    Args:
        totals (dict): Aggregated totals dictionary
        title (str): Title for the summary
    """
    print("\n" + "="*80)
    print(title)
    print("="*80)
    
    if not totals:
        print("\nNo videos found matching the criteria.")
        return
    
    for platform, res_formats in sorted(totals.items()):
        print(f"\n{platform.upper()}:")
        print("-" * 40)
        
        for res_format, stats in sorted(res_formats.items()):
            total_size = stats['total_size_bytes']
            count = stats['count']
            failed = stats.get('failed_count', 0)
            
            status = ""
            if failed > 0:
                status = f" ({failed} failed)"
            
            print(f"  {res_format}: {count} videos, Total: {format_size(total_size)}{status}")


def main():
    start_time = time.time()
    
    # Define manifest files and their platforms
    manifest_files = {
        'tvos15.json': 'Apple',
        'fireos8.json': 'Amazon',
        'comm1.json': 'Jetson Creative',
        'comm2.json': 'Robin Fourcade'
    }

    # Base path for manifest files
    base_path = Path('app/src/main/res/raw/')

    # Load cache
    print("Loading cache...")
    cache = load_cache()
    cached_count = len(cache)
    print(f"  Found {cached_count} cached video sizes")

    all_videos = []
    manifest_errors = 0

    print("\nLoading video manifests...")

    # Process each manifest file
    for filename, platform_name in manifest_files.items():
        file_path = base_path / filename

        if not file_path.exists():
            print(f"Warning: {file_path} not found, skipping...")
            manifest_errors += 1
            continue

        print(f"Processing {filename} ({platform_name})...")

        manifest_data = load_manifest(file_path)
        if manifest_data is None:
            manifest_errors += 1
            continue
            
        videos = extract_video_urls(manifest_data, platform_name)
        all_videos.extend(videos)
        print(f"  Found {len(videos)} videos")

    if not all_videos:
        print("\nError: No videos found in any manifest files.")
        if manifest_errors > 0:
            print(f"  {manifest_errors} manifest file(s) had errors.")
        return 1

    print(f"\nTotal videos found: {len(all_videos)}")

    # Get sizes for all videos using parallel HTTP requests
    print("\nFetching video sizes...")
    successful, failed = fetch_video_sizes_parallel(all_videos, cache)
    
    # Save updated cache
    print(f"\nSaving cache ({len(cache)} entries)...")
    save_cache(cache)

    print(f"\nFetch summary:")
    print(f"  Successful: {successful}")
    print(f"  Failed: {failed}")
    print(f"  Success rate: {(successful / len(all_videos) * 100):.1f}%")

    # Calculate and display totals
    print(f"\nCalculating totals...")
    totals = calculate_totals(all_videos)
    
    print_summary(totals)
    
    # Additional breakdowns
    print("\n" + "="*80)
    print("BREAKDOWN BY RESOLUTION AND FORMAT ACROSS ALL PLATFORMS")
    print("="*80)
    
    # Group by resolution and format
    res_format_totals = {}
    for video in all_videos:
        res_format = f"{video['resolution']}-{video['format']}"
        if res_format not in res_format_totals:
            res_format_totals[res_format] = {'count': 0, 'size': 0, 'failed': 0}
        res_format_totals[res_format]['count'] += 1
        if video['size_bytes']:
            res_format_totals[res_format]['size'] += video['size_bytes']
        else:
            res_format_totals[res_format]['failed'] += 1
    
    for res_format, stats in sorted(res_format_totals.items()):
        failed_str = f" ({stats['failed']} failed)" if stats['failed'] > 0 else ""
        print(f"{res_format}: {stats['count']} videos, Total: {format_size(stats['size'])}{failed_str}")
    
    # Allow filtering by command-line arguments
    if len(sys.argv) > 1:
        platform_filter = None
        resolution_filter = None
        format_filter = None
        
        for arg in sys.argv[1:]:
            if arg.startswith('--platform='):
                platform_filter = arg.split('=', 1)[1]
            elif arg.startswith('--resolution='):
                resolution_filter = arg.split('=', 1)[1]
            elif arg.startswith('--format='):
                format_filter = arg.split('=', 1)[1]
            elif arg == '--clear-cache':
                if os.path.exists(CACHE_FILE):
                    os.remove(CACHE_FILE)
                    print("\nCache cleared!")
                return 0
        
        if platform_filter or resolution_filter or format_filter:
            filtered_totals = calculate_totals(all_videos, platform_filter, resolution_filter, format_filter)
            filter_desc = f"Platform={platform_filter or 'Any'}, Resolution={resolution_filter or 'Any'}, Format={format_filter or 'Any'}"
            print_summary(filtered_totals, f"FILTERED RESULTS: {filter_desc}")

    # Calculate execution time
    end_time = time.time()
    execution_time = end_time - start_time
    
    print(f"\n" + "="*80)
    print(f"Total execution time: {execution_time:.2f} seconds")
    print("="*80)
    
    return 0


if __name__ == "__main__":
    sys.exit(main())
