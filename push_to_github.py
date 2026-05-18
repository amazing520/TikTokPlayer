#!/usr/bin/env python3
"""Push all files to GitHub via API (no git push needed)"""
import os, json, base64, urllib.request, sys

TOKEN = sys.argv[1]
OWNER = "amazing520"
REPO = "TikTokPlayer"
API = f"https://api.github.com/repos/{OWNER}/{REPO}"
HEADERS = {
    "Authorization": f"token {TOKEN}",
    "Accept": "application/vnd.github+json",
    "Content-Type": "application/json",
}

def api_call(method, path, data=None):
    url = f"{API}{path}" if path.startswith("/") else path
    body = json.dumps(data).encode() if data else None
    req = urllib.request.Request(url, data=body, headers=HEADERS, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read())
    except Exception as e:
        print(f"ERROR {method} {path}: {e}")
        if hasattr(e, 'read'):
            print(e.read().decode())
        sys.exit(1)

def create_blob(filepath):
    with open(filepath, "rb") as f:
        content = f.read()
    # Try text, fallback to base64
    try:
        text = content.decode("utf-8")
        data = {"content": text, "encoding": "utf-8"}
    except:
        data = {"content": base64.b64encode(content).decode(), "encoding": "base64"}
    result = api_call("POST", "/git/blobs", data)
    return result["sha"]

def collect_files(root):
    """Collect all files with their relative paths"""
    files = []
    for dirpath, dirnames, filenames in os.walk(root):
        # Skip hidden dirs and build dirs
        dirnames[:] = [d for d in dirnames if not d.startswith('.') and d != 'build']
        for fname in filenames:
            if fname.startswith('.'):
                continue
            full = os.path.join(dirpath, fname)
            rel = os.path.relpath(full, root)
            files.append((rel, full))
    return files

def main():
    root = os.path.dirname(os.path.abspath(__file__))
    files = collect_files(root)
    print(f"Found {len(files)} files to push")

    # Create blobs for all files
    tree_items = []
    for rel, full in files:
        print(f"  Creating blob: {rel}")
        sha = create_blob(full)
        tree_items.append({
            "path": rel,
            "mode": "100644",
            "type": "blob",
            "sha": sha
        })

    # Create tree
    print("Creating tree...")
    tree = api_call("POST", "/git/trees", {"tree": tree_items})
    tree_sha = tree["sha"]
    print(f"Tree SHA: {tree_sha}")

    # Create commit
    print("Creating commit...")
    commit = api_call("POST", "/git/commits", {
        "message": "feat: 仿抖音本地视频播放器 Android 项目\n\n- 竖屏全屏沉浸式播放，支持横屏切换\n- 上下滑动切换视频（VerticalPager）\n- 本地视频扫描（MediaStore）\n- 随机循环播放\n- 0.5x~3.0x 倍速调节\n- 定时关闭功能\n- Jetpack Compose + Media3 ExoPlayer",
        "tree": tree_sha
    })
    commit_sha = commit["sha"]
    print(f"Commit SHA: {commit_sha}")

    # Update ref (create or update)
    print("Updating ref...")
    try:
        api_call("PATCH", "/git/refs/heads/main", {"sha": commit_sha, "force": True})
    except:
        api_call("POST", "/git/refs", {"ref": "refs/heads/main", "sha": commit_sha})

    print(f"\n✅ Done! https://github.com/{OWNER}/{REPO}")

if __name__ == "__main__":
    main()
