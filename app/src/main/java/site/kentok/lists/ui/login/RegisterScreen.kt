package site.kentok.lists.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

@Composable
fun RegisterScreen(
    loading: Boolean,
    error: String?,
    offline: Boolean,
    onRegister: (nick: String, email: String, password: String) -> Unit,
    onBack: () -> Unit
) {
    var nick by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
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
                size = 96.dp
            )
            Text(
                "Регистрация",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "Ник от 3 символов, почта, пароль от 3 символов. После регистрации вы сразу в редакторе.",
                color = SiteMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            if (offline) {
                Text(
                    "Нужен интернет для регистрации",
                    color = SiteError,
                    modifier = Modifier.padding(top = 8.dp),
                    fontSize = 13.sp
                )
            }
            OutlinedTextField(
                value = nick,
                onValueChange = { nick = it },
                placeholder = { Text("Ник (минимум 3 символа)") },
                singleLine = true,
                colors = fieldColors,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = { Text("Почта") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = fieldColors,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text("Пароль (минимум 3 символа)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = fieldColors,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            if (error != null) {
                Text(error, color = SiteError, modifier = Modifier.padding(top = 10.dp), fontSize = 14.sp)
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp),
                    color = SiteBlue,
                    strokeWidth = 2.dp
                )
            } else {
                PrimarySiteButton(
                    text = "Зарегистрироваться",
                    onClick = { onRegister(nick.trim(), email.trim(), password) },
                    enabled = nick.length >= 3 && email.isNotBlank() && password.length >= 3 && !offline,
                    modifier = Modifier.padding(top = 12.dp)
                )
                SecondarySiteButton(
                    text = "Уже есть аккаунт — вход",
                    onClick = onBack,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
    }
}
