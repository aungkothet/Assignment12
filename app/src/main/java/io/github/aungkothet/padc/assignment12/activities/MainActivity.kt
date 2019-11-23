package io.github.aungkothet.padc.assignment12.activities

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import coil.api.load
import com.facebook.*
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import io.github.aungkothet.padc.assignment12.R
import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    lateinit var callbackManager: CallbackManager
    lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    lateinit var progressDialog: ProgressDialog

    private val RC_SIGN_IN = 12345

    override fun onStart() {
        super.onStart()
        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user != null) {
            updateUi(user.photoUrl.toString(), user.displayName, user.email, "Welcome Back")
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        callbackManager = CallbackManager.Factory.create()

        FacebookSdk.sdkInitialize(applicationContext)

        val userName = Observable.create<String> { emitter ->
            etUserName.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(p0: Editable?) {
                }

                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }

                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                    emitter.onNext(p0.toString())
                }

            })
        }

        val password = Observable.create<String> { emitter ->
            etPassword.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(p0: Editable?) {
                }

                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }

                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                    emitter.onNext(p0.toString())
                }

            })
        }


        Observable.combineLatest<String, String, Boolean>(
            userName,
            password,
            BiFunction { name, password ->
                name.length > 6 && password.length > 6 && Patterns.EMAIL_ADDRESS.matcher(name).matches()
            }
        ).subscribe {
            btnLogin.isEnabled = it
        }

        btnLogin.setOnClickListener {
            showLoading()
            signInWithEmailPassword(etUserName.text.toString(), etPassword.text.toString())
        }

        btnFacebook.setOnClickListener {
            showLoading()
            signInWithFacebook()
        }

        btnGoogle.setOnClickListener {
            showLoading()
            signInWithGoogle()
        }

        btnLogOut.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            layoutProfile.visibility = View.GONE
            etPassword.setText("")
            etUserName.setText("")
            loginForm.visibility = View.VISIBLE
            val accessToken = AccessToken.getCurrentAccessToken()
            val isFBLoggedIn = accessToken != null && !accessToken.isExpired
            if (isFBLoggedIn) {
                LoginManager.getInstance().logOut()
            }
        }

    }

    private fun showLoading() {
        progressDialog = ProgressDialog(this)
        progressDialog.setCancelable(false)
        if (!progressDialog.isShowing && !isFinishing) {
            progressDialog.show()
        }
    }

    fun hideLoading() {
        if (progressDialog.isShowing) {
            progressDialog.dismiss()
        }
    }

    //Facebook sign in
    private fun signInWithFacebook() {
        LoginManager.getInstance().logInWithReadPermissions(
            this,
            listOf("public_profile", "name", "email")
        )
        LoginManager.getInstance().registerCallback(callbackManager, object :
            FacebookCallback<LoginResult> {
            override fun onSuccess(result: LoginResult?) {
                if (result != null) {
                    handleFacebookAccessToken(result.accessToken)
                } else {
                    Log.e("FB_LOGIN", "RESult Null")
                }
            }

            override fun onCancel() {
                hideLoading()

            }

            override fun onError(error: FacebookException?) {
                Log.e("FB_LOGIN", error.toString())
            }

        })
    }

    //Google sign in
    private fun signInWithGoogle() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    // Email pass sign in
    private fun signInWithEmailPassword(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d("EMAIL_PW_LOGIN", "signInWithEmail:success")
                    val user = auth.currentUser
                    hideLoading()
                    user?.let {
                        updateUi(
                            user.photoUrl.toString(),
                            user.displayName,
                            user.email,
                            "Email/Password"
                        )
                    }
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w("EMAIL_PW_LOGIN", "signInWithEmail:failure", task.exception)
                    Toast.makeText(
                        baseContext, "Authentication failed.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

    }

    private fun updateUi(
        profilePhoto: String?,
        userName: String?,
        email: String?,
        providerType: String
    ) {
        loginForm.visibility = View.GONE
        layoutProfile.visibility = View.VISIBLE
        profilePhoto?.let {
            ivProfile.load(profilePhoto)
        }
        userName?.let {
            tvName.text = userName
        }
        email?.let {
            tvEmail.text = email
        }
        tvProvider.text = providerType

    }

    private fun handleFacebookAccessToken(token: AccessToken) {
        Log.d("FB_LOGIN", "handleFacebookAccessToken:$token")

        val credential = FacebookAuthProvider.getCredential(token.token)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d("FB_LOGIN", "signInWithCredential:success")
                    val user = auth.currentUser
                    hideLoading()
                    user?.let {
                        Log.d(
                            "FB_LOGIN",
                            "${user.displayName}, ${user.email}, ${user.phoneNumber}, ${user.photoUrl} "
                        )
                        updateUi(user.photoUrl.toString(), user.displayName, user.email, "Facebook")
                    }
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w("FB_LOGIN", "signInWithCredential:failure", task.exception)
                    Toast.makeText(
                        baseContext, "Authentication failed.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    //For google authentication
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        callbackManager.onActivityResult(requestCode, resultCode, data)
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data) as Task
            try {
                val account = task.getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account?.idToken, null)
                auth.signInWithCredential(credential)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            hideLoading()

                            user?.let {
                                updateUi(
                                    user.photoUrl.toString(),
                                    user.displayName,
                                    user.email,
                                    "Google"
                                )
                            }
                        } else {
                            Log.w("GOOGLE_LOGIN", "signInWithCredential:failure", task.exception)
                        }
                    }
            } catch (e: ApiException) {
                Log.w("GOOGLE_LOGIN", "Google sign in failed", e)
            }
        }
    }
}