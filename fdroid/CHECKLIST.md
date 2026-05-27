# F-Droid Submission Checklist

## Pre-submission
- [ ] App builds from source: `./gradlew clean assembleDebug`
- [ ] APK installs and runs correctly
- [ ] No proprietary dependencies
- [ ] No ads, analytics, tracking
- [ ] No unnecessary permissions
- [ ] GPL-3.0 LICENSE exists in root

## For fdroiddata merge request
- [ ] Fork https://gitlab.com/fdroid/fdroiddata
- [ ] Create metadata file: `metadata/com.pindownloader.yml`
- [ ] Set `subdir: app`
- [ ] Set current version tag
- [ ] Verify build with FDroid build server
- [ ] Submit merge request

## Useful resources
- https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- https://gitlab.com/fdroid/fdroiddata
