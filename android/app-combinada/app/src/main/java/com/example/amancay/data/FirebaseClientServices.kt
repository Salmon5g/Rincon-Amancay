package com.example.amancay.data

import android.content.Context
import com.example.amancay.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

data class FirebaseClientServices(
    val auth: FirebaseAuth,
    val firestore: FirebaseFirestore
)

object FirebaseClientServicesProvider {
    fun initialize(context: Context): FirebaseClientServices {
        val defaultApp = FirebaseApp.getInstance()
        val app = if (BuildConfig.FIREBASE_EMULATORS) {
            val appName = "amancay-emuladores"
            FirebaseApp.getApps(context).firstOrNull { it.name == appName }
                ?: FirebaseApp.initializeApp(
                    context,
                    FirebaseOptions.Builder()
                        .setApplicationId(defaultApp.options.applicationId!!)
                        .setApiKey(defaultApp.options.apiKey!!)
                        .setProjectId("demo-rincon-amancay")
                        .setStorageBucket("demo-rincon-amancay.appspot.com")
                        .build(),
                    appName
                )!!
        } else {
            defaultApp
        }
        val auth = FirebaseAuth.getInstance(app)
        val firestore = FirebaseFirestore.getInstance(app)
        if (BuildConfig.FIREBASE_EMULATORS) {
            auth.useEmulator("10.0.2.2", 9099)
            firestore.useEmulator("10.0.2.2", 8080)
        }
        return FirebaseClientServices(auth, firestore)
    }
}
