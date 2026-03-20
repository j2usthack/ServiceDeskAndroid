package com.example.servicedeskapk.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.servicedeskapk.ServiceDeskApp
import com.example.servicedeskapk.data.api.RetrofitClient
import com.example.servicedeskapk.data.model.ChangePasswordRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChangePasswordViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = (application as ServiceDeskApp).preferencesManager

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    data class State(
        val newPassword: String = "",
        val newPasswordConfirm: String = "",
        val isLoading: Boolean = false,
        val error: String? = null,
        val success: Boolean = false
    )

    fun onPasswordChanged(value: String) {
        _state.value = _state.value.copy(newPassword = value, error = null)
    }

    fun onPasswordConfirmChanged(value: String) {
        _state.value = _state.value.copy(newPasswordConfirm = value, error = null)
    }

    fun changePassword() {
        val s = _state.value
        if (s.newPassword.length < 6) {
            _state.value = s.copy(error = "Пароль должен быть не менее 6 символов"); return
        }
        if (s.newPassword != s.newPasswordConfirm) {
            _state.value = s.copy(error = "Пароли не совпадают"); return
        }

        viewModelScope.launch {
            _state.value = s.copy(isLoading = true, error = null)
            try {
                val api = RetrofitClient.getApiService(getApplication(), prefs.serverUrl)
                val response = api.changePassword(
                    ChangePasswordRequest(s.newPassword, s.newPasswordConfirm)
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    prefs.mustChangePassword = false
                    _state.value = _state.value.copy(isLoading = false, success = true)
                } else {
                    val errorBody = response.errorBody()?.string()
                    val msg = try {
                        com.google.gson.Gson().fromJson(
                            errorBody,
                            com.example.servicedeskapk.data.model.SimpleResponse::class.java
                        )?.error
                    } catch (_: Exception) { null }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = msg ?: "Ошибка смены пароля"
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
fun ChangePasswordScreen(
    onPasswordChanged: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    viewModel: ChangePasswordViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.success) {
        if (state.success) onPasswordChanged()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Смена пароля") },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.LockReset,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Необходимо сменить пароль",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Ваш пароль был сброшен администратором. Установите новый пароль для продолжения работы.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = state.newPassword,
                onValueChange = viewModel::onPasswordChanged,
                label = { Text("Новый пароль") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                enabled = !state.isLoading
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.newPasswordConfirm,
                onValueChange = viewModel::onPasswordConfirmChanged,
                label = { Text("Подтвердите пароль") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { viewModel.changePassword() }),
                enabled = !state.isLoading
            )

            state.error?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = viewModel::changePassword,
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
                    Text("Сохранить пароль")
                }
            }
        }
    }
}
