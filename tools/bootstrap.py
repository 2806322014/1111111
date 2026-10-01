import concurrent.futures, hashlib, json, pathlib, subprocess, zipfile

ROOT = pathlib.Path.home() / '.cache' / 'shiguang-build'
ROOT.mkdir(parents=True, exist_ok=True)

def fetch(url):
    script = "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8; $r=Invoke-WebRequest -Uri '" + url + "'; if($r.Content -is [byte[]]){[Text.Encoding]::UTF8.GetString($r.Content)}else{$r.Content}"
    return subprocess.check_output(['pwsh', '-NoProfile', '-Command', script]).strip()

def install(name, url, digest, dest):
    archive = ROOT / (name + '.zip')
    if not archive.exists():
        print('Downloading ' + name, flush=True)
        script = "$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri '" + url + "' -OutFile '" + str(archive) + "'"
        subprocess.run(['pwsh', '-NoProfile', '-Command', script], check=True)
    actual = hashlib.sha256(archive.read_bytes()).hexdigest()
    if actual != digest:
        raise ValueError('Checksum mismatch: ' + name)
    if not dest.exists():
        print('Extracting ' + name, flush=True)
        with zipfile.ZipFile(archive) as z:
            z.extractall(dest)
    print('Ready: ' + name, flush=True)

if __name__ == '__main__':
    jdk = json.loads(fetch('https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows'))[0]['binary']['package']
    gradle_hash = fetch('https://services.gradle.org/distributions/gradle-8.9-bin.zip.sha256').decode().strip()
    jobs = [
        ('jdk21', jdk['link'], jdk['checksum'], ROOT / 'jdk'),
        ('gradle89', 'https://services.gradle.org/distributions/gradle-8.9-bin.zip', gradle_hash, ROOT / 'gradle'),
        ('android-tools', 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip', '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a', ROOT / 'sdk' / 'cmdline-tools' / 'staging')
    ]
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        for result in pool.map(lambda args: install(*args), jobs):
            pass
    sdk = ROOT / 'sdk' / 'cmdline-tools'
    if not (sdk / 'latest').exists():
        (sdk / 'staging' / 'cmdline-tools').rename(sdk / 'latest')
    print('Build tools downloaded and verified.', flush=True)
