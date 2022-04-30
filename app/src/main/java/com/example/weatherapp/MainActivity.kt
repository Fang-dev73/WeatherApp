package com.example.weatherapp

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import retrofit.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    // A fused location client variable which is further user to get the user's current location
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    // A global variable for Progress Dialog
    private var mProgressDialog : Dialog? = null

    //A global variable for SharedPreference used to store previous data
    private lateinit var mSharedPreferences : SharedPreferences

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the Fused location variable
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize the SharedPreferences variable
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        // Call the setupUI method
        setupUI()

        if(!isLocationEnabled()){
            Toast.makeText(this,"Your location is off. Please turn it on"
                ,Toast.LENGTH_SHORT).show()

            // This will redirect to settings from where the location should be given access if cancelled before
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            Dexter.withActivity(this)
                .withPermissions(
                    ACCESS_FINE_LOCATION,ACCESS_COARSE_LOCATION
            )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()){

                            requestLocationData()

                        }

                        if(report.isAnyPermissionPermanentlyDenied)
                        {
                            Toast.makeText(this@MainActivity,
                                "You have denied location permission.Please allow it."
                                ,Toast.LENGTH_SHORT).show()
                        }

                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermission()
                    }

                }).onSameThread().check()
        }

    }

    // A function used to show the alert dialog box when the permissions are denied
    private fun showRationalDialogForPermission(){
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off the permission.")
            .setPositiveButton("Go to Settings"
            ) { _, _ ->
                try{
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package",packageName,null)
                    intent.data = uri
                    startActivity(intent)
                }catch(e : ActivityNotFoundException){
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel"){ dialog, _->
                dialog.dismiss()
            }.show()
    }

    // A function to request the current location.
    @SuppressLint("MissingPermission")
    private fun requestLocationData(){

        val mLocationRequest = com.google.android.gms.location.LocationRequest()
        mLocationRequest.priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest,mLocationCallback,
            Looper.myLooper()
        )

    }

    // A location callback to get the co-ordinates of user's position
    private val mLocationCallback = object : LocationCallback(){
        override fun onLocationResult(locationResult : LocationResult){
            val mLastLocation : Location =locationResult.lastLocation
            val latitude = mLastLocation.latitude
            Log.i("Current Latitude","$latitude")

            val longitude =mLastLocation.longitude
            Log.i("Current Longitude","$longitude")
            getLocationWeatherDetails(latitude , longitude)
        }
    }

    // Function is used to get the weather details of the current location based on co-ordinates
    private fun getLocationWeatherDetails(latitude : Double , longitude : Double){
        if(Constants.isNetworkAvailable(this@MainActivity)){
            val retrofit : Retrofit = Retrofit.Builder()
                    // API base URL
                .baseUrl(Constants.BASE_URL)
                    // Gson converter to parse an HTTP request into kotlin objects
                .addConverterFactory(GsonConverterFactory.create())
                // Create the Retrofit instances
                .build()

            /**
            Here the service interface mapping is done in which we declare the end point and the API type
            i.e GET, POST and so on along with the request parameter
            */

            val service : WeatherService = retrofit

                .create(WeatherService::class.java)

            // Here the required parameters in the service are passed

            val listCall : Call<WeatherResponse> = service.getWeather(
                latitude,longitude,Constants.METRIC_UNIT,Constants.APP_ID
            )

            // Callback of progress dialog box
            showCustomProgressDialog()

            // Callback methods are executed using the Retrofit callback executor.
            listCall.enqueue(object : Callback<WeatherResponse>{
                @RequiresApi(Build.VERSION_CODES.N)
                override fun onResponse(response: Response<WeatherResponse>?, retrofit: Retrofit?) {
                    if(response!!.isSuccess){

                        hideProgressDialog() // To hide the progress dialog once the response is ready

                        // Here we have converted the model class in to Json String to store it in the SharedPreferences.
                        val weatherList : WeatherResponse = response.body()

                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        // Save the converted string to shared preferences
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

                        setupUI()   // Calling the setupUI before checking the response

                        // To Check weather the response is success or not.

                        Log.i("Response Result" , "$weatherList")
                    }else{
                        when(response.code()){
                            400 -> {
                                Log.e("Error 400" , "Bad Connection")
                            }
                            404 -> {
                                Log.e("Error 404" , "Not Found")
                            }
                            else -> {
                                Log.e("Error" , "Generic Error")
                            }
                        }
                    }
                }

                override fun onFailure(t: Throwable?) {
                    // Hides the progress dialog
                    Log.e("Error!!!" , t!!.message.toString())
                    hideProgressDialog()
                }

            })

        }else{
            Toast.makeText(this@MainActivity,"No internet connection.",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun isLocationEnabled() : Boolean{

        // This provides access to the system location services.
        val locationManager : LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    }

    // Method used to show the Custom Progress Dialog.

    private fun showCustomProgressDialog(){
        mProgressDialog = Dialog(this)

    //Set the screen content from a layout resource.
    mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)

        //Start the dialog and display it on screen.
    mProgressDialog!!.show()
}


override fun onCreateOptionsMenu(menu: Menu?): Boolean {
menuInflater.inflate(R.menu.menu_main,menu)
return super.onCreateOptionsMenu(menu)
}

    // Refresh Button
override fun onOptionsItemSelected(item: MenuItem): Boolean {
return when(item.itemId){
    R.id.action_refresh -> {
        requestLocationData()
        true
    }
    else -> super.onOptionsItemSelected(item)

    }

}

// Used to dismiss the progress dialog

private fun hideProgressDialog(){
if (mProgressDialog != null){
    mProgressDialog!!.dismiss()
}
}

    // Function is used to set the result in the UI elements.
@RequiresApi(Build.VERSION_CODES.N)
private fun setupUI(){

        // Here we get the stored response from SharedPreference

val weatherResponseJsonString = mSharedPreferences
    .getString(Constants.WEATHER_RESPONSE_DATA,"")

if(!weatherResponseJsonString.isNullOrEmpty()){

    val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)

    // For loop to get the required data and all are created in the UI.

    for(i in weatherList.weather.indices){
        Log.i("Weather Name", weatherList.weather.toString())

        tv_main.text = weatherList.weather[i].main
        tv_main_description.text = weatherList.weather[i].description
        tv_temp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())

        tv_humidity.text = weatherList.main.humidity.toString() + "percent"
        tv_min.text = weatherList.main.temp_min.toString() + "min"
        tv_max.text = weatherList.main.temp_max.toString() + "max"
        tv_speed.text = weatherList.wind.speed.toString()
        tv_name.text =weatherList.name
        tv_country.text = weatherList.sys.country
        tv_sunrise_time.text = unixTime(weatherList.sys.sunrise)
        tv_sunset_time.text = unixTime(weatherList.sys.sunset)

        // Here we update the main icon
        when(weatherList.weather[i].icon){
            "01d" -> iv_main.setImageResource(R.drawable.sunny)
            "02d" -> iv_main.setImageResource(R.drawable.cloud)
            "03d" -> iv_main.setImageResource(R.drawable.cloud)
            "04d" -> iv_main.setImageResource(R.drawable.cloud)
            "04n" -> iv_main.setImageResource(R.drawable.cloud)
            "010d" -> iv_main.setImageResource(R.drawable.rain)
            "011d" -> iv_main.setImageResource(R.drawable.storm)
            "013d" -> iv_main.setImageResource(R.drawable.snowflake)
            "01n" -> iv_main.setImageResource(R.drawable.cloud)
            "02n" -> iv_main.setImageResource(R.drawable.cloud)
            "03n" -> iv_main.setImageResource(R.drawable.cloud)
            "10n" -> iv_main.setImageResource(R.drawable.cloud)
            "11n" -> iv_main.setImageResource(R.drawable.rain)
            "13n" -> iv_main.setImageResource(R.drawable.snowflake)
        }
    }

}

}

    // Function is used to get the temperature unit value.
private fun getUnit(value: String):String?{
var value = "°C"
if("US" == value || "LR" == value || "MM" == value){
    value = "°F"
}
return value

}

    //The function is used to get the formatted time based on the Format and the LOCALE we pass to it.

private fun unixTime(timex : Long) : String?{
val date = Date(timex * 1000L)
val sdf = SimpleDateFormat("HH:mm", Locale.UK)
sdf.timeZone = TimeZone.getDefault()
return sdf.format(date)
}

}