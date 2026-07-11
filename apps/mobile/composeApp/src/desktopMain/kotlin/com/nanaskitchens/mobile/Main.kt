package com.nanaskitchens.mobile
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
fun main() = application { Window(onCloseRequest = ::exitApplication, title = "Nanas' Kitchens", state = WindowState(width = 430.dp, height = 850.dp)) { App() } }
