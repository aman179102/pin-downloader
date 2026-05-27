# F-Droid Submission Guide

## Prerequisites
- A [GitLab account](https://gitlab.com/users/sign_in)
- Your app builds and runs correctly
- GPL-3.0 LICENSE file in repository root
- A tagged release (v1.0.0 exists)

## Step-by-step

### 1. Fork fdroiddata
Go to https://gitlab.com/fdroid/fdroiddata and click **Fork**.

### 2. Clone your fork
```bash
git clone https://gitlab.com/YOUR_USERNAME/fdroiddata.git
cd fdroiddata
```

### 3. Add the metadata file
Copy the template from this repo:
```
fdroid/com.pindownloader.yml
```

Into your fdroiddata clone:
```
cp /path/to/com.pindownloader.yml fdroiddata/metadata/com.pindownloader.yml
```

### 4. Commit and push
```bash
git add metadata/com.pindownloader.yml
git commit -m "Add PinDownloader (com.pindownloader)"
git push origin main
```

### 5. Open a Merge Request
- Go to your fork on GitLab
- Click **Create merge request**
- Target: `fdroid/fdroiddata` → `main`
- Title: `Add PinDownloader (com.pindownloader)`
- Description: Briefly describe the app and note it is GPL-3.0-only

### 6. Respond to reviewers
F-Droid maintainers may ask questions about:
- Build reproducibility
- Dependency licenses
- Permission usage
- Network requests

Reply promptly and update the metadata if requested.

## Notes
- Do not claim "F-Droid accepted" until the merge request is merged.
- You can check build status at https://f-droid.org/en/packages/com.pindownloader/
- Initial acceptance can take weeks to months.
