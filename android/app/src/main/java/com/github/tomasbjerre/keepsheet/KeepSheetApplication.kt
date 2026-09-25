package com.github.tomasbjerre.keepsheet

import android.app.Application

/**
 * Manual, framework-free service locator. The app is small enough that a
 * DI framework would add more ceremony than it removes.
 *
 * TODO: wire up the Room database/repository here once
 * specs/data-model.md's Document/Page storage is implemented (see
 * WispApplication in the sibling wisp repo for the pattern this follows).
 */
class KeepSheetApplication : Application()
