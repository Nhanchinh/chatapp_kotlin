package com.example.chatapp.utils

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

@Composable
fun rememberImagePickerLauncher(onImageSelected: (Uri?) -> Unit) =
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        onImageSelected(uri)
    }

@Composable
fun rememberVideoPickerLauncher(onVideoSelected: (Uri?) -> Unit) =
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        onVideoSelected(uri)
    }

@Composable
fun rememberFilePickerLauncher(onFileSelected: (Uri?) -> Unit) =
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        onFileSelected(uri)
    }

