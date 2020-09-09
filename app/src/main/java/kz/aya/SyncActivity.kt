package kz.aya

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.Exclude
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.android.synthetic.main.activity_sync.*

class SyncActivity : AppCompatActivity() {
//    https://www.androdocs.com/kotlin/getting-current-location-latitude-longitude-in-android-using-kotlin.html
//    https://medium.com/@manuaravindpta/fetching-contacts-from-device-using-kotlin-6c6d3e76574f

    //    val database = Firebase.database
    private lateinit var database: DatabaseReference
    private lateinit var user_database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var user: FirebaseUser


    companion object {
        val PERMISSION_ID = 42
        val PERMISSIONS_REQUEST_READ_CONTACTS = 100
    }

    private var latlong = HashMap<String, Any>()
    private var contacts = HashMap<String, Any>()

    lateinit var mFusedLocationClient: FusedLocationProviderClient
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sync)
        setupFb()
    }


    private fun setupFb() {
        database = FirebaseDatabase.getInstance().reference
        auth = FirebaseAuth.getInstance()
        auth.currentUser?.let {
            user = it
            user_database = database.child("users").child(user.uid)
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            getLastLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastLocation() {
        if (checkPermissions()) {
            if (isLocationEnabled()) {

                mFusedLocationClient.lastLocation.addOnCompleteListener(this) { task ->
                    var location: Location? = task.result
                    if (location == null) {
                        requestNewLocationData()
                    } else {
                        user_database.child("location").setValue(Coords(location.latitude, location.longitude).toMap())

                        findViewById<TextView>(R.id.latTextView).text = location.latitude.toString()
                        findViewById<TextView>(R.id.lonTextView).text =
                            location.longitude.toString()
                    }
                }
            } else {
                Toast.makeText(this, "Turn on location", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
            loadContacts()
        } else {
            requestPermissions()
        }
    }


    @SuppressLint("MissingPermission")
    private fun requestNewLocationData() {
        var mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.interval = 0
        mLocationRequest.fastestInterval = 0
        mLocationRequest.numUpdates = 1

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mFusedLocationClient!!.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            var mLastLocation: Location = locationResult.lastLocation
            user_database.child("location").setValue(Coords(mLastLocation.latitude, mLastLocation.longitude).toMap())

            findViewById<TextView>(R.id.latTextView).text = mLastLocation.latitude.toString()
            findViewById<TextView>(R.id.lonTextView).text = mLastLocation.longitude.toString()
        }
    }

    private fun isLocationEnabled(): Boolean {
        var locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun checkPermissions(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return false
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            PERMISSION_ID
        )
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_ID) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                getLastLocation()
//                loadContacts()
            }
        }
    }

    //    ****************** CONTACTS ***********************
    private fun loadContacts() {
        var builder = StringBuilder()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestContactsPermissions()
//            requestPermissions(
//                arrayOf(Manifest.permission.READ_CONTACTS),
//                PERMISSIONS_REQUEST_READ_CONTACTS
//            )
            //callback onRequestPermissionsResult
        } else {
            builder = getContacts()
            listContacts.text = builder.toString()
        }
    }

    private fun requestContactsPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.READ_CONTACTS
            ),
            PERMISSION_ID
        )
    }

    private fun getContacts(): StringBuilder {
        val builder = StringBuilder()
        val resolver: ContentResolver = contentResolver;
        val cursor = resolver.query(
            ContactsContract.Contacts.CONTENT_URI, null, null, null,
            null
        )

        if (cursor!!.count > 0) {
            val contacts_reference = user_database.child("contacts")
            while (cursor.moveToNext()) {
                val id = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID))
                val name =
                    cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))
                val phoneNumber = (cursor.getString(
                    cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                )).toInt()

                if (phoneNumber > 0) {
                    val cursorPhone = contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?",
                        arrayOf(id),
                        null
                    )

                    if (cursorPhone!!.count == 1) {
                        while (cursorPhone.moveToNext()) {
                            val phoneNumValue = cursorPhone.getString(
                                cursorPhone.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            )
                            contacts_reference.push().updateChildren(Contact(name, phoneNumValue).toMap())
                            builder.append("Contact: ").append(name).append(", Phone Number: ")
                                .append(
                                    phoneNumValue
                                ).append("\n\n")
                            Log.e("Name ===>", phoneNumValue);
                        }
                    }
                    cursorPhone.close()
                }
            }
            Toast.makeText(this, "Your data stored successfully", Toast.LENGTH_LONG).show()
        } else {
            //   toast("No contacts available!")
        }
        cursor.close()
//        pushToFb()
        return builder
    }
}


@IgnoreExtraProperties
data class Contact(
    var name: String? = "",
    var phoneNumber: String? = ""
) {

    @Exclude
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "name" to name,
            "phoneNumber" to phoneNumber
        )
    }
}
@IgnoreExtraProperties
data class Coords(
    var lat: Double? = .0,
    var lon: Double? = .0
) {

    @Exclude
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "lat" to lat,
            "lon" to lon
        )
    }
}