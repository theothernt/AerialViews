import os
import subprocess

def get_video_bitrate(filepath):
    """
    Retrieves the bitrate of a video file using ffprobe.

    Args:
        filepath (str): The path to the video file.

    Returns:
        int: The bitrate in bits per second, or None if an error occurs.
    """
    try:
        command = [
            'ffprobe',
            '-v', 'error',
            '-select_streams', 'v:0',
            '-show_entries', 'stream=bit_rate',
            '-of', 'default=noprint_wrappers=1:nokey=1',
            filepath,
        ]
        result = subprocess.run(command, capture_output=True, text=True, check=True)
        bitrate = int(result.stdout.strip())
        return bitrate
    except (FileNotFoundError, subprocess.CalledProcessError, ValueError) as e:
        print(f"Error processing {filepath}: {e}")
        return None

def calculate_hourly_data_gb(bitrate_bps):
    """
    Calculates the amount of data used in an hour given the bitrate in gigabytes.

    Args:
        bitrate_bps (int): The bitrate in bits per second.

    Returns:
        float: The data usage in gigabytes per hour.
    """
    if bitrate_bps is None:
        return None
    seconds_in_hour = 3600
    bits_in_gigabyte = 8 * 1024 * 1024 * 1024
    data_usage_bits = bitrate_bps * seconds_in_hour
    data_usage_gb = data_usage_bits / bits_in_gigabyte
    return data_usage_gb

def process_video_files(folder_path):
    """
    Processes all video files in a folder, calculates their bitrates,
    estimates hourly data usage, and provides a summary.

    Args:
        folder_path (str): The path to the folder containing video files.
    """
    try:
        files = [os.path.join(folder_path, f) for f in os.listdir(folder_path) if os.path.isfile(os.path.join(folder_path, f))]
        
        total_bitrate = 0
        file_count = 0
        
        print("--- Individual File Analysis ---")
        for filepath in files:
            bitrate = get_video_bitrate(filepath)
            if bitrate is not None:
                hourly_data_gb = calculate_hourly_data_gb(bitrate)
                if hourly_data_gb is not None:
                    print(f"{os.path.basename(filepath)}: Bitrate: {bitrate} bps, Hourly data: {hourly_data_gb:.2f} GB")
                    total_bitrate += bitrate
                    file_count += 1
        
        print("\n--- Summary ---")
        if file_count > 0:
            average_bitrate = total_bitrate / file_count
            average_hourly_data_gb = calculate_hourly_data_gb(average_bitrate)
            
            print(f"Total files processed: {file_count}")
            print(f"Average bitrate: {int(average_bitrate)} bps")
            print(f"Average hourly data per file: {average_hourly_data_gb:.2f} GB")
        else:
            print("No video files found or processed.")

    except FileNotFoundError:
        print(f"Folder not found: {folder_path}")
    except Exception as e:
        print(f"An error occurred: {e}")

if __name__ == "__main__":
    folder = input("Enter the folder path: ")
    process_video_files(folder)