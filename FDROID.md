# Publishing Spinet to F-Droid

Spinet is FOSS (GPL-3.0-or-later) with no proprietary dependencies, so it is
eligible for F-Droid. It talks to no service of its own accord, only the user's
own server and whatever extensions the user installs.

The build includes the extension contract from the `Spinet-Modular` repository,
checked out beside this one (`includeBuild("../Spinet-Modular")`). F-Droid builds
from one repository, so that contract has to be published and fetchable, as a
git submodule or a published artifact, before a submission can build.

## Prerequisites (already done / to confirm)

- [x] `GPL-3.0-or-later` LICENSE in the repo root.
- [x] No Google Play Services, Firebase, analytics, or other proprietary SDKs.
- [x] All dependencies FOSS (Apache-2.0 / GPLv3), fetched from Maven Central.
- [x] `applicationId` set (`dev.spinet`) and `versionCode`/`versionName` in
      `composeApp/build.gradle.kts`.
- [x] Fastlane metadata under `fastlane/metadata/android/en-US/`.
- [ ] **Verify a clean release build** from a fresh checkout:
      `./gradlew :composeApp:assembleRelease` (must succeed with no local-only steps).
- [ ] Add launcher **icon** and **screenshots** so the listing isn't bare (see below).
- [ ] Tag the release commit: `git tag v1.0 && git push --tags`. F-Droid builds from a tag.

## Graphics to add (optional but recommended)

```
fastlane/metadata/android/en-US/
  images/
    icon.png                 512x512
    phoneScreenshots/
      1.png  2.png  3.png     (PNG/JPG, actual app screenshots)
```

## The two ways onto F-Droid

**A. Official F-Droid repo** (`f-droid.org`, built + signed by F-Droid):
1. Read <https://f-droid.org/docs/Inclusion_Policy> and
   <https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide>.
2. Open a **Request For Packaging (RFP)** issue at
   <https://gitlab.com/fdroid/rfp/-/issues> with the repo URL.
3. Then submit a metadata **merge request** to `fdroiddata` with a build recipe
   (`metadata/dev.spinet.yml`) roughly like:

```yaml
Categories:
  - Multimedia
License: GPL-3.0-or-later
SourceCode: https://github.com/Kevcar98/Spinet-Mobile
IssueTracker: https://github.com/Kevcar98/Spinet-Mobile/issues
RepoType: git
Repo: https://github.com/Kevcar98/Spinet-Mobile.git

Builds:
  - versionName: "1.0"
    versionCode: 1
    commit: v1.0
    subdir: composeApp
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: "1.0"
CurrentVersionCode: 1
```

Their buildserver compiles from source and signs with the F-Droid key. Expect
review back-and-forth.

**B. Your own F-Droid repo** (fastest, you control it): publish signed APKs with
`fdroidserver` (`fdroid update`) to a static host / GitHub Pages, and share the repo
URL. Good for friends now; you can still pursue the official repo later. See
<https://f-droid.org/docs/Setup_an_F-Droid_App_Repo>.

## IzzyOnDroid (middle ground)

<https://apt.izzysoft.de/fdroid/> ingests signed GitHub Releases with fastlane
metadata. It is a much lighter process than the official repo, and it already respects the
layout you have. Often the quickest path to a real, updatable listing.
