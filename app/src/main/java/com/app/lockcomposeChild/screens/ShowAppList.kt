import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowAppList() {
    val context = LocalContext.current
    val appsList = remember { mutableStateOf<List<InstalledApps>>(emptyList()) }
    val isLoading = remember { mutableStateOf(true) }
    val selectedApps = remember { mutableStateOf<MutableSet<InstalledApps>>(mutableSetOf()) }
    val showToast = remember { mutableStateOf(false) }

    // Firebase listener to fetch and update the app list in real-time
    DisposableEffect(Unit) {
        val firebaseDatabase = FirebaseDatabase.getInstance().reference.child("Apps")

        val valueEventListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val updatedList = mutableListOf<InstalledApps>()

                for (childSnapshot in dataSnapshot.children) {
                    val packageName = childSnapshot.child("package_name").getValue(String::class.java) ?: ""
                    val name = childSnapshot.child("name").getValue(String::class.java) ?: ""
                    val base64Icon = childSnapshot.child("icon").getValue(String::class.java) ?: ""
                    val interval = childSnapshot.child("interval").getValue(String::class.java) ?: ""
                    val pinCode = childSnapshot.child("pin_code").getValue(String::class.java) ?: ""
                    val isIconVisible = childSnapshot.child("isIconVisible").getValue(Boolean::class.java) ?: false

                    val iconBitmap = base64ToBitmap(base64Icon)

                    val installedApp = InstalledApps(
                        packageName = packageName,
                        name = name,
                        icon = iconBitmap,
                        interval = interval,
                        pinCode = pinCode,
                        isIconVisible = isIconVisible
                    )
                    updatedList.add(installedApp)
                }
                appsList.value = updatedList
                isLoading.value = false
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        }

        firebaseDatabase.addValueEventListener(valueEventListener)
        onDispose {
            firebaseDatabase.removeEventListener(valueEventListener)
        }
    }

    if (showToast.value) {
        Toast.makeText(context, "Data sent successfully", Toast.LENGTH_SHORT).show()
        showToast.value = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Child App", color = Color.Black)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.LightGray
                )
            )
        },
        content = { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // List of apps
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(appsList.value) { app ->
                            val isSelected = selectedApps.value.contains(app)
                            AppListItem(
                                app = app,
                                interval = app.interval,
                                pinCode = app.pinCode,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelected) {
                                        selectedApps.value.remove(app)
                                    } else {
                                        selectedApps.value.add(app)
                                    }
                                    selectedApps.value = selectedApps.value.toSet().toMutableSet()
                                }
                            )
                        }
                    }

                    // Fixed position button
                    Button(
                        onClick = {
                            // Toggle visibility for selected apps before uploading
                            toggleIconVisibility(selectedApps.value.toList(), appsList)
                            uploadToFirebase(selectedApps.value.toList())
                            showToast.value = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(65.dp)
                            .padding(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3F51B5)
                        ),
                        shape = RectangleShape
                    ) {
                        Text(
                            text = "Submit",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun AppListItem(
    app: InstalledApps,
    interval: String,
    pinCode: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) {
        Color.Blue // Border color when selected
    } else {
        Color.Transparent // No border when not selected
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp) // Margin around the card
            .border(2.dp, borderColor, RectangleShape) // Border color based on selection
            .clickable { onClick() }, // Clickable row
        elevation = CardDefaults.cardElevation(4.dp),
        shape = RectangleShape
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Only show the icon if isIconVisible is true
                if (app.isIconVisible) {
                    Image(
                        bitmap = app.icon.asImageBitmap(),
                        contentDescription = app.name,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                } else {
                    Spacer(modifier = Modifier.size(64.dp)) // Spacer to maintain layout
                }

                Text(
                    text = app.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Interval and Pin Code Section
            Column(
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = "Interval: $interval Min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Pin Code: $pinCode",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// Function to upload data to Firebase
fun uploadToFirebase(appsList: List<InstalledApps>) {
    val firebaseDatabase = FirebaseDatabase.getInstance().reference.child("childApps")

    appsList.forEach { app ->
        val appData = mapOf(
            "package_name" to app.packageName,
            "icon" to bitmapToBase64(app.icon),
            "interval" to app.interval,
            "pin_code" to app.pinCode,
            "isIconVisible" to app.isIconVisible
        )

        firebaseDatabase.child(app.name).setValue(appData)
    }
}

// Function to toggle isIconVisible for selected apps and update locally
fun toggleIconVisibility(appsList: List<InstalledApps>, appsListState: MutableState<List<InstalledApps>>) {
    val firebaseDatabase = FirebaseDatabase.getInstance().reference

    appsList.forEach { app ->
        val newVisibility = !app.isIconVisible  // Toggle visibility

        val appData = mapOf(
            "isIconVisible" to newVisibility
        )

        // Update the visibility in Firebase
        firebaseDatabase.child("Apps").child(app.name.toLowerCase()).updateChildren(appData)

        // Update the local app list to reflect the new visibility state
        val updatedAppsList = appsListState.value.map {
            if (it.packageName == app.packageName) {
                it.copy(isIconVisible = newVisibility)
            } else {
                it
            }
        }
        appsListState.value = updatedAppsList // This will trigger recomposition
    }
}

// Function to convert Bitmap to Base64
fun bitmapToBase64(bitmap: Bitmap): String {
    val byteArrayOutputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
    val byteArray = byteArrayOutputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.DEFAULT)
}

// Function to convert Base64 to Bitmap
fun base64ToBitmap(base64Str: String): Bitmap {
    val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
    return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
}

data class InstalledApps(
    val packageName: String,
    val name: String,
    val icon: Bitmap,
    val interval: String,
    val pinCode: String,
    val isIconVisible: Boolean
)
