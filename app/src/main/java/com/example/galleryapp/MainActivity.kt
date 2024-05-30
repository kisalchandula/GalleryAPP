package com.example.galleryapp

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Scale
import com.example.galleryapp.ui.theme.GalleryAppTheme
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GalleryAppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    GalleryApp()
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun GalleryApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "gallery") {
        composable("gallery") { GalleryScreen(navController) }
        composable(
            "image/{imageUri}",
            arguments = listOf(navArgument("imageUri") { type = NavType.StringType })
        ) { backStackEntry ->
            val imageUriString = backStackEntry.arguments?.getString("imageUri")
            if (imageUriString != null) {
                val imageUri = Uri.parse(imageUriString)
                FullScreenImage(imageUri, navController)
            } else {
                Text("Image not found")
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(navController: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Photos") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        val context = LocalContext.current
        val readStoragePermissionGranted = remember { mutableStateOf(false) }
        val imageUris = remember { mutableStateListOf<Uri>() }
        val requestPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                readStoragePermissionGranted.value = true
                imageUris.clear()
                imageUris.addAll(fetchImageUris(context))
            } else {
                readStoragePermissionGranted.value = false
            }
        }

        LaunchedEffect(Unit) {
            when {
                ContextCompat.checkSelfPermission(
                    context,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_IMAGES
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }
                ) == PackageManager.PERMISSION_GRANTED -> {
                    readStoragePermissionGranted.value = true
                    imageUris.clear()
                    imageUris.addAll(fetchImageUris(context))
                }

                else -> {
                    requestPermissionLauncher.launch(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Manifest.permission.READ_MEDIA_IMAGES
                        } else {
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                    )
                }
            }
        }

        if (readStoragePermissionGranted.value) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(imageUris.size) { index ->
                    GalleryItem(imageUri = imageUris[index], onClick = {
                        val encodedUri = URLEncoder.encode(imageUris[index].toString(), StandardCharsets.UTF_8.toString())
                        navController.navigate("image/$encodedUri")
                    })
                }
            }
        } else {
            // Handle the case where permission is not granted
            Text("Permission to read storage is required to display images.")
        }
    }
}

fun fetchImageUris(context: android.content.Context): List<Uri> {
    val imageUris = mutableListOf<Uri>()
    val projection = arrayOf(
        MediaStore.Images.Media._ID
    )
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    val query = context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        sortOrder
    )

    query?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val contentUri = ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                id
            )
            imageUris.add(contentUri)
        }
    }
    return imageUris
}

@Composable
fun GalleryItem(imageUri: Uri, onClick: () -> Unit) {
    Image(
        painter = rememberAsyncImagePainter(
            ImageRequest.Builder(LocalContext.current).data(data = imageUri).apply {
                size(150, 150)
                scale(Scale.FILL)
                crossfade(true)
                allowHardware(false)
                allowRgb565(true)
                diskCachePolicy(CachePolicy.DISABLED)
                memoryCachePolicy(CachePolicy.ENABLED)
                networkCachePolicy(CachePolicy.ENABLED)
            }.build()
        ),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(2.dp))
            .clickable { onClick() }
    )
}

@Composable
fun FullScreenImage(imageUri: Uri, navController: NavHostController) {
    Box(modifier = Modifier
        .fillMaxSize()
        .clickable { navController.popBackStack() }) {
        Image(
            painter = rememberAsyncImagePainter(model = imageUri),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}
