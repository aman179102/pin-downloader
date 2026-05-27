# IzzyOnDroid Submission Guide

IzzyOnDroid accepts app submissions more quickly than F-Droid.

## How to submit

### Option 1: Request form
Go to: https://apt.izzysoft.de/fdroid/index.php

Click **Request to add an app** and fill in:

| Field | Value |
|---|---|
| App name | PinDownloader |
| Package name | com.pindownloader |
| Source code | https://github.com/aman179102/pin-downloader |
| License | GPL-3.0-only |
| Latest version | v1.0.0 |
| What it does | Android downloader app with share intent support. No ads, no tracking, no accounts. |

### Option 2: Via GitLab
Fork https://gitlab.com/IzzyOnDroid/repo

Add:
```
metadata/com.pindownloader.yml
```

With the same content as `fdroid/com.pindownloader.yml`.

Then open a merge request.

## Expectations
- IzzyOnDroid typically accepts within a few days
- Builds are done on Izzy's servers
- Updates are picked up automatically from GitHub Releases
- The app will appear at: https://apt.izzysoft.de/fdroid/index.php?repo=stable
