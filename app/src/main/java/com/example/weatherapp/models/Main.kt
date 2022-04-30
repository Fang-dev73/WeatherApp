package com.example.weatherapp.models

import java.io.Serializable

data class Main (
    val temp : Double,
    val temp_min : Double,
    val temp_max : Double,
    val pressure : Double,
    val humidity : Int
    ) : Serializable