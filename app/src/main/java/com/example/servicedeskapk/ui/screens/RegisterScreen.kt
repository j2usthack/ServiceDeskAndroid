package com.example.servicedeskapk.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.servicedeskapk.ServiceDeskApp
import com.example.servicedeskapk.data.api.RetrofitClient
import com.example.servicedeskapk.data.model.RegisterRequest
import com.example.servicedeskapk.push.FcmRegistrationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RegisterViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = (application as ServiceDeskApp).preferencesManager

    private val _state = MutableStateFlow(RegisterState())
    val state: StateFlow<RegisterState> = _state

    data class RegisterState(
        val username: String = "",
        val password: String = "",
        val passwordConfirm: String = "",
        val firstName: String = "",
        val lastName: String = "",
        val isLoading: Boolean = false,
        val error: String? = null,
        val success: Boolean = false
    )

    fun onFieldChanged(field: String, value: String) {
        _state.value = when (field) {
            "username" -> _state.value.copy(username = value, error = null)
            "password" -> _state.value.copy(password = value, error = null)
            "passwordConfirm" -> _state.value.copy(passwordConfirm = value, error = null)
            "firstName" -> _state.value.copy(firstName = value, error = null)
            "lastName" -> _state.value.copy(lastName = value, error = null)
            else -> _state.value
        }
    }

    fun register() {
        val s = _state.value
        if (s.username.isBlank()) {
            _state.value = s.copy(error = "Имя пользователя обязательно"); return
        }
        if (s.password.length < 6) {
            _state.value = s.copy(error = "Пароль должен содержать минимум 6 символов"); return
        }
        if (s.password != s.passwordConfirm) {
            _state.value = s.copy(error = "Пароли не совпадают"); return
        }

        viewModelScope.launch {
            _state.value = s.copy(isLoading = true, error = null)
            try {
                val api = RetrofitClient.getApiService(getApplication(), prefs.serverUrl)
                val response = api.register(
                    RegisterRequest(s.username, s.password, s.passwordConfirm, s.firstName, s.lastName)
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val user = response.body()?.user
                    if (user != null) {
                        prefs.saveUser(
                            id = user.id,
                            username = user.username,
                            firstName = user.firstName,
                            lastName = user.lastName,
                            isAdmin = user.isAdmin,
                            isSuperAdmin = user.isSuperAdmin,
                            mustChangePassword = user.mustChangePassword
                        )
                        FcmRegistrationHelper.registerTokenAfterLogin(getApplication())
                    }
                    _state.value = _state.value.copy(isLoading = false, success = true)
                } else {
                    val errorBody = response.errorBody()?.string()
                    val msg = try {
                        com.google.gson.Gson().fromJson(
                            errorBody,
                            com.example.servicedeskapk.data.model.AuthResponse::class.java
                        )?.error
                    } catch (_: Exception) { null }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = msg ?: response.body()?.error ?: "Ошибка регистрации"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Ошибка подключения: ${e.localizedMessage}"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: RegisterViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.success) {
        if (state.success) onRegisterSuccess()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Регистрация") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = state.firstName,
                onValueChange = { viewModel.onFieldChanged("firstName", it) },
                label = { Text("Имя") },
                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                enabled = !state.isLoading
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.lastName,
                onValueChange = { viewModel.onFieldChanged("lastName", it) },
                label = { Text("Фамилия") },
                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                enabled = !state.isLoading
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.username,
                onValueChange = { viewModel.onFieldChanged("username", it) },
                label = { Text("Имя пользователя *") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                enabled = !state.isLoading
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.password,
                onValueChange = { viewModel.onFieldChanged("password", it) },
                label = { Text("Пароль *") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                enabled = !state.isLoading
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.passwordConfirm,
                onValueChange = { viewModel.onFieldChanged("passwordConfirm", it) },
                label = { Text("Подтвердите пароль *") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { viewModel.register() }),
                enabled = !state.isLoading
            )

            state.error?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = viewModel::register,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !state.isLoading,
                shape = MaterialTheme.shapes.medium
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Зарегистрироваться")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
