# Set destination path
$path = "src/main/res/raw"

# Make sure raw directory exists
if (!(Test-Path $path))
{
    New-Item -ItemType Directory -Path $path
}

# Check if json files are already there
$num = (Get-ChildItem -Path $path -Recurse -Filter "tvos*.json").Count

# Skip download if files already exist
if ($num -ne 1)
{
    #wget http://sylvan.apple.com/Aerials/resources-13.tar |  tar -xOf - entries.json
    #(Invoke-WebRequest -URI http://sylvan.apple.com/Aerials/resources-13.tar).Content 
    #curl.exe -s http://sylvan.apple.com/Aerials/resources-13.tar | tar -xOf - entries.json
    
    #curl.exe http://sylvan.apple.com/Aerials/resources-13.tar | tar -xOf - entries.json > $path/tvos13.json
    #curl.exe -s http://sylvan.apple.com/Aerials/resources-13.tar | tar -xf - entries.json

    curl.exe http://sylvan.apple.com/Aerials/resources-13.tar -o resources-13.tar
    tar -xOf resources-13.tar entries.json > $path/tvos13.json
    Remove-Item resources-13.tar
}
