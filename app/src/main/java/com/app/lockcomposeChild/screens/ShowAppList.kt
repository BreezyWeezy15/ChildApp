import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
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
                        isIconVisible = isIconVisible // Add the fetched value here
                    )
                    updatedList.add(installedApp)
                }
                appsList.value = updatedList
                isLoading.value = false
            }

            override fun onCancelled(databaseError: DatabaseError) {
                isLoading.value = false
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
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading.value) {
                    CircularProgressIndicator()
                } else {
                    if (appsList.value.isEmpty()) {
                        Text(
                            text = "No apps added yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSystemInDarkTheme()) Color.White else Color.Black,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
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
                                        }
                                    )
                                }
                            }

                            // Submit button
                            Button(
                                onClick = {
                                    // Update isIconVisible to true for selected apps before uploading
                                    updateIconVisibility(selectedApps.value.toList(), true)
                                    uploadToFirebase(selectedApps.value.toList())
                                    showToast.value = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp),
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
                // Check if the icon is visible
                if (!app.isIconVisible) { // Show the icon when isIconVisible is false
                    Image(
                        bitmap = app.icon.asImageBitmap(),
                        contentDescription = app.name,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
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


data class InstalledApps(
    val packageName: String,
    val name: String,
    val icon: Bitmap,
    val interval: String,
    val pinCode: String,
    val isIconVisible: Boolean // Added this property
) {
    override fun equals(other: Any?): Boolean {
        return other is InstalledApps && this.packageName == other.packageName
    }

    override fun hashCode(): Int {
        return packageName.hashCode()
    }
}

fun base64ToBitmap(base64Str: String): Bitmap {
    val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
    return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
}

// Function to upload data to Firebase
fun uploadToFirebase(appsList: List<InstalledApps>) {
    val firebaseDatabase = FirebaseDatabase.getInstance().reference.child("childApps") // Change to the correct path

    appsList.forEach { app ->
        val appData = mapOf(
            "package_name" to app.packageName,
            "icon" to bitmapToBase64(app.icon),
            "interval" to app.interval,
            "pin_code" to app.pinCode,
            "isIconVisible" to app.isIconVisible // Ensure this is included in the upload
        )

        firebaseDatabase.child(app.name).setValue(appData) // Updated to use packageName
    }
}

// Function to update isIconVisible for selected apps
fun updateIconVisibility(appsList: List<InstalledApps>, isVisible: Boolean) {
    val firebaseDatabase = FirebaseDatabase.getInstance().reference

    appsList.forEach { app ->
        val appData = mapOf(
            "isIconVisible" to isVisible
        )

        // Navigate to the correct path: app.name/Apps and update the visibility
        firebaseDatabase.child("Apps").child(app.name.toLowerCase()).updateChildren(appData)
    }
}

// Function to convert Bitmap to Base64
fun bitmapToBase64(bitmap: Bitmap): String {
    val byteArrayOutputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
    val byteArray = byteArrayOutputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.DEFAULT)
}
