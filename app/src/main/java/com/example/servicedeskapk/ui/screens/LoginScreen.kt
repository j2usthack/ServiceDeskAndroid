package com.example.servicedeskapk.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.example.servicedeskapk.data.model.LoginRequest
import com.example.servicedeskapk.push.FcmRegistrationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = (application as ServiceDeskApp).preferencesManager

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state

    data class LoginState(
        val username: String = "",
        val password: String = "",
        val isLoading: Boolean = false,
        val error: String? = null,
        val loginSuccess: Boolean = false,
        val mustChangePassword: Boolean = false
    )

    fun onUsernameChanged(value: String) {
        _state.value = _state.value.copy(username = value, error = null)
    }

    fun onPasswordChanged(value: String) {
        _state.value = _state.value.copy(password = value, error = null)
    }

    fun login() {
        val current = _state.value
        if (current.username.isBlank() || current.password.isBlank()) {
            _state.value = current.copy(error = "Заполните все поля")
            return
        }

        viewModelScope.launch {
            _state.value = current.copy(isLoading = true, error = null)
            try {
                val api = RetrofitClient.getApiService(
                    getApplication(),
                    prefs.serverUrl
                )
                val response = api.login(LoginRequest(current.username, current.password))
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true && body.user != null) {
                        prefs.saveUser(
                            id = body.user.id,
                            username = body.user.username,
                            firstName = body.user.firstName,
                            lastName = body.user.lastName,
                            isAdmin = body.user.isAdmin,
                            isSuperAdmin = body.user.isSuperAdmin,
                            mustChangePassword = body.user.mustChangePassword
                        )
                        FcmRegistrationHelper.registerTokenAfterLogin(getApplication())
                        _state.value = _state.value.copy(
                            isLoading = false,
                            loginSuccess = true,
                            mustChangePassword = body.user.mustChangePassword
                        )
                    } else {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            error = body?.error ?: "Ошибка авторизации"
                        )
                    }
                } else {
                    val code = response.code()
                    val errorBody = response.errorBody()?.string()
                    val errorMsg = try {
                        com.google.gson.Gson().fromJson(
                            errorBody,
                            com.example.servicedeskapk.data.model.AuthResponse::class.java
                        )?.error
                    } catch (_: Exception) { null }
                    val displayError = when {
                        errorMsg != null -> errorMsg
                        code == 404 -> "Сервер вернул 404. Проверьте адрес: приложение ожидает путь /api/auth/login. Если бэкенд отдаётся по подпути (например /myapp/), укажите в настройках: https://хост:порт/myapp/"
                        else -> "Ошибка авторизации ($code)"
                    }
                    _state.value = _state.value.copy(isLoading = false, error = displayError)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Ошибка подключения: ${e.localizedMessage ?: "Проверьте адрес сервера"}"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (mustChangePassword: Boolean) -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.loginSuccess) {
        if (state.loginSuccess) {
            onLoginSuccess(state.mustChangePassword)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Иконка
            Icon(
                Icons.Default.SupportAgent,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Управление заявками",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "IT-отдел университета",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Поле логина
            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChanged,
                label = { Text("Имя пользователя") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                enabled = !state.isLoading
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Поле пароля
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChanged,
                label = { Text("Пароль") },
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
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { viewModel.login() }),
                enabled = !state.isLoading
            )

            // Ошибка
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

            // Кнопка входа
            Button(
                onClick = viewModel::login,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
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
                    Text("Войти", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Ссылка на регистрацию
            TextButton(onClick = onNavigateToRegister) {
                Text("Нет аккаунта? Зарегистрироваться")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
