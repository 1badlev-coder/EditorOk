package site.kentok.lists.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.kentok.lists.ui.components.BrandLogo
import site.kentok.lists.ui.theme.SiteBg
import site.kentok.lists.ui.theme.SiteBlue
import site.kentok.lists.ui.theme.SiteError
import site.kentok.lists.ui.theme.SiteMuted
import site.kentok.lists.ui.theme.SiteOk

@Composable
fun LoginScreen(
    loading: Boolean,
    error: String?,
    success: String?,
    offline: Boolean,
    onLogin: (String, String) -> Unit,
    onRegister: () -> Unit,
    onRecover: () -> Unit
) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = SiteBlue,
        unfocusedBorderColor = Color(0xFFBBBBBB),
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SiteBg)
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(16.dp))
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(22.dp)
        ) {
            BrandLogo(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                size = 112.dp
            )
            Text(
                "Многоуровневые списки",
                color = SiteMuted,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp, bottom = 12.dp)
            )
            if (offline) {
                Text(
                    "Нет сети — первый вход недоступен",
                    color = SiteError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    fontSize = 13.sp
                )
            }
            OutlinedTextField(
                value = login,
                onValueChange = { login = it },
                placeholder = { Text("Ник или почта") },
                singleLine = true,
                colors = fieldColors,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text("Пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = fieldColors,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            if (error != null) {
                Text(
                    error,
                    color = SiteError,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    fontSize = 14.sp
                )
            }
            if (success != null) {
                Text(
                    success,
                    color = SiteOk,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    fontSize = 14.sp
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                RegPill(onClick = onRegister)
                RecoverPill(onClick = onRecover)
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 16.dp),
                    color = SiteBlue,
                    strokeWidth = 2.dp
                )
            } else {
                LoginPill(
                    onClick = { onLogin(login, password) },
                    enabled = login.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
    }
}
