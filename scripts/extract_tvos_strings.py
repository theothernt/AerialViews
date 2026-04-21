import os
import subprocess

def convert_to_android_code(lproj_name):
    lang = lproj_name.replace('.lproj', '')
    
    # Special mappings for Android
    mapping = {
        'en': '', # Default raw folder
        'id': 'in',
        'he': 'iw',
        'zh_CN': 'zh-rCN',
        'zh_HK': 'zh-rHK',
        'zh_TW': 'zh-rTW',
        'es_419': 'es-rUS', # Common mapping for Latin America in Android
    }
    
    if lang in mapping:
        return mapping[lang]
    
    # General rule: replace _ with -r
    return lang.replace('_', '-r')

def main():
    # Paths are relative to the project root
    bundle_path = 'scripts/TVIdleScreenStrings.bundle'
    res_path = 'app/src/main/res'
    target_filename = 'tvos26_strings.json'
    
    if not os.path.exists(bundle_path):
        print(f"Error: Bundle not found at {bundle_path}")
        return

    # Get all .lproj directories
    lproj_dirs = [d for d in os.listdir(bundle_path) if d.endswith('.lproj')]
    lproj_dirs.sort()

    for item in lproj_dirs:
        lproj_path = os.path.join(bundle_path, item)
        strings_file = os.path.join(lproj_path, 'Localizable.nocache.strings')
        
        if os.path.exists(strings_file):
            android_code = convert_to_android_code(item)
            
            if android_code:
                target_dir = os.path.join(res_path, f'raw-{android_code}')
            else:
                target_dir = os.path.join(res_path, 'raw')
            
            # Ensure the target directory exists
            os.makedirs(target_dir, exist_ok=True)
            target_path = os.path.join(target_dir, target_filename)
            
            print(f"Converting {item} -> {target_path}")
            
            # Use plutil to convert to JSON and save
            try:
                # We are on Darwin, so plutil is available
                subprocess.run(['plutil', '-convert', 'json', '-o', target_path, strings_file], check=True)
            except subprocess.CalledProcessError as e:
                print(f"Failed to convert {strings_file}: {e}")
            except FileNotFoundError:
                print("Error: 'plutil' command not found. This script requires macOS.")
                return

if __name__ == "__main__":
    main()
