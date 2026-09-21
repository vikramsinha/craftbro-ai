#!/usr/bin/env python3
"""Relaunch only this project's Fabric development client."""
import os
from pathlib import Path
import signal
import subprocess
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
os.chdir(ROOT)

def game_pids():
    output = subprocess.check_output(['/bin/ps', '-axo', 'pid=,command='], text=True)
    return [int(line.strip().split(None, 1)[0]) for line in output.splitlines()
            if str(ROOT) in line and '/bin/java ' in line
            and (('-Dfabric.dli.env=client' in line and 'net.fabricmc.devlaunchinjector.Main' in line)
                 or 'net.minecraft.client.main.Main' in line)]

def ready(url):
    try:
        with urllib.request.urlopen(url, timeout=2):
            return True
    except Exception:
        return False

def service(url, launcher, log):
    if ready(url):
        return
    with open(ROOT / '.tools' / log, 'ab') as output:
        subprocess.Popen(['/bin/zsh', str(ROOT / launcher)], stdin=subprocess.DEVNULL,
                         stdout=output, stderr=output, start_new_session=True)
    for _ in range(90):
        if ready(url):
            return
        time.sleep(1)
    raise RuntimeError(f'{launcher} did not become ready. See .tools/{log}.')

def main():
    import fcntl
    (ROOT / '.tools').mkdir(exist_ok=True)
    with open(ROOT / '.tools/relaunch.lock', 'w') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            print('A relaunch is already in progress.')
            return
        pids = game_pids()
        if pids:
            print('Closing the existing development game...', flush=True)
            request = ROOT / 'run/hellomod-relaunch.request'
            request.parent.mkdir(exist_ok=True)
            request.touch()
            try:
                for _ in range(15):
                    if not game_pids():
                        break
                    time.sleep(1)
                # Compatibility with versions installed before the relaunch handler.
                for pid in set(pids) & set(game_pids()):
                    os.kill(pid, signal.SIGTERM)
                for _ in range(45):
                    if not game_pids():
                        break
                    time.sleep(1)
                if game_pids():
                    raise RuntimeError('Minecraft is still closing. Close it normally, then click the shortcut again.')
            finally:
                request.unlink(missing_ok=True)
        print('Preparing Aspire and local AI...', flush=True)
        service('http://127.0.0.1:18888', 'Launch Aspire Dashboard.command', 'aspire-launch.log')
        service('http://127.0.0.1:11434/api/version', 'Launch Local AI.command', 'ollama-launch.log')
        print('Building and starting Minecraft with telemetry...', flush=True)
        env = dict(os.environ, HELLOMOD_TELEMETRY_ENABLED='true', OTEL_EXPORTER_OTLP_ENDPOINT='http://127.0.0.1:4318')
        process = subprocess.Popen(['./gradlew', '--gradle-user-home', '.gradle-user-home', 'runClient'], env=env)
        # Release once the window process exists, so another Dock click can relaunch it.
        while process.poll() is None and not game_pids():
            time.sleep(1)
        fcntl.flock(lock, fcntl.LOCK_UN)
    code = process.wait()
    if code and code != 143:
        raise RuntimeError(f'Minecraft exited with status {code}. See the output above.')

if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(f'Could not relaunch: {error}', flush=True)
        raise SystemExit(1)
