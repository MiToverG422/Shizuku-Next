# KernelSU-Mi Material alignment

Reference: [KernelSU-Mi Material UI, revision 5dfa282](https://github.com/MiToverG422/KernelSU-Mi/tree/5dfa282d045728fa24c830d0afecedc9481266f0/manager/app/src/main/java/me/weishu/kernelsu/ui).
Shizuku-specific screens use their existing service, permission, pairing and preference
APIs. This is an implementation of that Material design in Shizuku, not a replacement
of Shizuku functionality with KernelSU functionality.

## Component mapping

| Reference | Shizuku implementation |
| --- | --- |
| theme/Type.kt | ui/theme/Typography.kt: explicit regular Typography, body 16/24 sp, letter spacing 0.5 sp; supporting text 14 sp, navigation label 12 sp |
| theme/MaterialTheme.kt, ThemeExt.kt | ShizukuTheme: MaterialExpressiveTheme, expressive MotionScheme, MaterialKolor TonalSpot with explicit SPEC_2025 (user-requested override), animated color roles, system day/night/dynamic and AMOLED palettes |
| BottomBarMaterial floating variant | FloatingNavigationBar: 56 dp capsule; 6/5 dp inner horizontal/vertical padding, 6 dp gaps, 3 dp tonal/6 dp shadow elevation; 250 ms FastOutSlowIn color and label expansion/fade |
| BottomBar.kt / PagerNavigationSpring.kt | Pixel-driven Animatable pager scrolling without distant-page pre-jump; latest-request cancellation and selection synchronization; shared spring: stiffness 322.2, damping 32.31 / (2 * sqrt(322.2)), threshold 0.5 |
| LargeFlexibleTopAppBar / ExpressiveScaffold | Shared MaterialTopAppBar, surfaceContainer, safe-drawing insets; separate saved collapse state per main page |
| SegmentedList.kt | Native Material 3 SegmentedListItem and segmentedShapes, including animated pressed shapes; surfaceBright; shared row/switch interaction source; 2 dp grouping gaps |
| SegmentedItemContainer | SegmentedCard for rich content: activation methods, rish tutorial steps, and pairing network instructions/steps now share adjacent inner corners; status and warning sections remain separate |
| HomeMaterial / SuperUserMaterial icons | Regular list/navigation icons 24 dp; application icons 48 dp, including bitmap decoding and fallback icons (previously 40 dp) |
| ExpressiveSwitch.kt | Shared icon-enabled switch: check when on, cross when off; reference icon sizing and disabled colors; icons enabled by default as requested |
| TonalCard.kt / HomeMaterial.kt | Material large shapes; compact secondary-container status row with titleMediumEmphasized and labelSmallEmphasized mode tag; 16 dp page padding, 13 dp section spacing |
| ExpressiveMenu.kt | MaterialChoiceRow: touch-anchored DropdownMenuPopup and grouped selectable menu items |
| NavigationRailMaterial.kt | WideNavigationRail with animated expand/collapse and remembered preference |

The same components now cover home, apps, settings, terminal, activation methods,
pairing tutorial, rish tutorial, starter output, ADB discovery/pairing, about/stop,
permission confirmation and legacy compatibility dialogs. Terminal output uses
monospace bodySmall like the reference execution screen.

The main pager retains visited fragments and terminal sessions. Embedded Compose
pages explicitly forward scroll events to their page's app bar: an AndroidFragment
boundary otherwise prevents the outer app bar from collapsing. Bottom clearance is
passed through the same boundary while the floating capsule overlays the content.

Home information order: uptime (when running), app version, model, Android version.
Uptime has no navigation callback; the authorized-count card has been removed.
The top service status still opens activation methods, where the stop action and its
confirmation dialog now live.

## Build requirements

- Kotlin/Compose compiler 2.4.10, Compose BOM 2026.08.00.
- Material 3 1.5.0-alpha27 and MaterialKolor 5.0.1, matching the reference.
- AGP 9.2.1, Gradle 9.4.1, JDK 21, compile SDK 37.
- Non-final resource IDs are required for AGP 9's optimized Release resource shrinking.
  R8 rules only suppress missing optional WindowManager vendor interfaces; shrinking
  stays enabled and device-provided extension stubs are not bundled into the APK.
- Runtime compatibility is unchanged: min SDK 24, target SDK 36.
- Legacy AGP DSL/Kotlin opt-outs remain for the existing Rikka plugins. Root plugin
  declarations keep AGP and Kotlin on a shared classloader.
- The nonexistent/unused hidden-api-stub project include was removed.
- JVM tests use original signed Conscrypt/Bouncy Castle jars before Android's
  instrumented jars; application crypto configuration and signatures are not changed.

## Validation (2026-09-11)

Commands (JDK 21):

```
gradlew.bat :manager:testDebugUnitTest --tests "*Material*Test" --tests "*AdbPairingServiceTest" :manager:assemble
```

Fifteen Material tests cover type/icon tokens, floating navigation selection, 200% font scale,
single switch dispatch/disabled rows, visible press feedback, rendering, fragment
bottom clearance, dialog dismissal and embedded-page app-bar collapse. New regressions
verify check/cross/disabled icon selection, explicit SPEC_2025, continuous distant-page
animation without a pre-jump, and rapid navigation cancellation retaining the latest target.
Pixel assertions also verify rich-card outer/inner corners and the 2 dp segment gap.
The activation-list regression checks that asynchronous status loading does not anchor
the list below its newly inserted stop action. Six additional pairing-service cases
cover shutdown/watchdog behavior; see [stability investigation](stability-2026-09-11.md).
Generated screenshots/reports live under manager/build/reports and are not committed.

Device smoke tests on OPPO PMA120, Android 16, Chinese/dark theme:

- Installed the Debug APK with replacement, without clearing data.
- Opened all four tabs; checked floating selection, per-page toolbar and settings
  toolbar collapse on scroll.
- Opened/dismissed the theme chooser without changing the preference.
- Navigated into activation methods and back without starting/stopping the service.
- Ran the read-only command `echo UI-check`: exit 0; output survived tab changes.
- Existing running ADB service and authorization were retained; no new AndroidRuntime
  crash appeared during these checks.
- Follow-up build: installed again without clearing data; verified both check and cross
  switch glyphs on the settings page with the explicit SPEC_2025 palette. Pager motion
  and retargeting are covered by the controlled-frame Compose tests above.
- Latest layout pass: checked the home information order and joined activation/rish
  cards on the device. Application-icon sizing and pairing-card grouping were checked
  in source and shared rendering tests; no pairing or authorization operation was run.

The new AGP writes the final APK to manager/build/outputs/apk/debug; do not install
stale files remaining in build/intermediates/apk/debug from earlier tooling.

Production lint reports 180 existing repository errors (translations, legacy format strings,
TV manifest and API-level calls). On Windows the Android-test lint phase also hits
a locked migrated Compose lint jar. Production lint can be inspected with:

```
gradlew.bat :manager:lintDebug -x :manager:lintAnalyzeDebugAndroidTest -x :manager:lintAnalyzeDebugUnitTest --no-daemon --max-workers=1
```

Remaining device acceptance: light/dynamic palette variations, real tablet/landscape,
rotation and process recreation, Android 7 compatibility, root/wireless activation,
pairing, grant/revoke flows and secure-lock reboot. These are not claimed as tested.
The direct-boot WorkManager initializer exclusion is preserved.
