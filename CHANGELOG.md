## 0.0.1 (2026-09-26)

### Features

-  automatic cropping and perspective correction (#39) ([42f05](https://github.com/tomasbjerre/keepsheet/commit/42f058ae67ba42e) Tomas Bjerre)  
-  suggest file names from recognized text (#38) ([e9b20](https://github.com/tomasbjerre/keepsheet/commit/e9b20c6a29fd57a) Tomas Bjerre)  
-  recognize text on-device with Tesseract (OCR) (#37) ([f2a52](https://github.com/tomasbjerre/keepsheet/commit/f2a522ce779eac6) Tomas Bjerre)  
-  apply color/grayscale/black-and-white filters to pages (#36) ([9a9f5](https://github.com/tomasbjerre/keepsheet/commit/9a9f53de2c4572d) Tomas Bjerre)  
-  implement PDF merging (#31) ([66691](https://github.com/tomasbjerre/keepsheet/commit/666919a0599511e) Tomas Bjerre)  
-  clear documents on every fresh app start (#11) (#23) ([f6cec](https://github.com/tomasbjerre/keepsheet/commit/f6cecf6f7f22509) Tomas Bjerre)  
-  add Home's Information dialog (#18) (#22) ([83cef](https://github.com/tomasbjerre/keepsheet/commit/83cef88425dbe0c) Tomas Bjerre)  
-  implement Document Detail screen with share and delete (#19) ([ccad2](https://github.com/tomasbjerre/keepsheet/commit/ccad23124977b07) Tomas Bjerre)  
-  implement camera capture for scanning pages (#9) (#14) ([de05b](https://github.com/tomasbjerre/keepsheet/commit/de05b26eddfb0f9) Tomas Bjerre)  
-  implement Import (photos to PDF) (#7) ([f7663](https://github.com/tomasbjerre/keepsheet/commit/f7663052a95da22) Tomas Bjerre)  
-  add Document/Page persistence, wire Home to real data (#4) ([1b1ae](https://github.com/tomasbjerre/keepsheet/commit/1b1ae073a013c15) Tomas Bjerre)  
-  bootstrap KeepSheet with spec-driven Android implementation ([63526](https://github.com/tomasbjerre/keepsheet/commit/63526cdc38293d4) Tomas Bjerre)  

### Bug Fixes

-  **ui**  keep save button visible on page review (#42) ([1d2ef](https://github.com/tomasbjerre/keepsheet/commit/1d2ef97a3cc4ef7) Tomas Bjerre)  
-  gate Capture's shutter until the camera is actually bound (#15) ([6136b](https://github.com/tomasbjerre/keepsheet/commit/6136b9e999aa0cd) Tomas Bjerre)  

### Dependency updates

- upgrade to Gradle 9, AGP 9, and compileSdk 37 (#30) ([f6128](https://github.com/tomasbjerre/keepsheet/commit/f61282be0a72053) Tomas Bjerre)  
- update gradle/actions action to v6 (#27) ([88921](https://github.com/tomasbjerre/keepsheet/commit/88921251d1b2eaf) renovate[bot])  
- update dependency org.jetbrains.kotlinx:kotlinx-coroutines-test to v1.11.0 (#24) ([3ac26](https://github.com/tomasbjerre/keepsheet/commit/3ac26e0a37337f7) renovate[bot])  
- update camerax (core, camera2, lifecycle, view) to v1.6.2 (#5) ([93aa3](https://github.com/tomasbjerre/keepsheet/commit/93aa336a3475032) renovate[bot])  
- update dependency androidx.camera:camera-camera2 to v1.6.2 ([59a51](https://github.com/tomasbjerre/keepsheet/commit/59a51b523bfe472) renovate[bot])  
- update dependency androidx.activity:activity-compose to v1.13.0 ([adc75](https://github.com/tomasbjerre/keepsheet/commit/adc757e18102744) renovate[bot])  
### Other changes

**Merge pull request #1 from tomasbjerre/renovate/androidx.activity-activity-compose-1.x**

* chore(deps): update dependency androidx.activity:activity-compose to v1.13.0 

[77a24](https://github.com/tomasbjerre/keepsheet/commit/77a249cdb98db34) Tomas Bjerre *2026-09-25 15:23:29*

**Merge pull request #2 from tomasbjerre/renovate/androidx.camera-camera-camera2-1.x**

* chore(deps): update dependency androidx.camera:camera-camera2 to v1.6.2 

[94fed](https://github.com/tomasbjerre/keepsheet/commit/94fed06062d983d) Tomas Bjerre *2026-09-25 15:23:20*


