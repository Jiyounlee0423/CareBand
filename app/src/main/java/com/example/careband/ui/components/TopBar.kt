package com.example.careband.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.careband.R
import com.example.careband.data.model.UserType
import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CareBandTopBar(
    isLoggedIn: Boolean,
    userType: UserType?,
    userName: String,
    onMenuClick: () -> Unit,
    onProfileClick: () -> Unit,
    onLogoClick: () -> Unit // 🔽 로고 클릭 이벤트 추가
) {
    CenterAlignedTopAppBar(
        title = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 56.dp), // 오른쪽 아이콘 공간 확보용
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.splash_icon),
                    contentDescription = "CareBand Logo",
                    modifier = Modifier
                        .height(28.dp)
                        .clickable { onLogoClick() }, // 🔽 클릭 시 이벤트 실행,
                    colorFilter = ColorFilter.tint(Color.White)
                )
            }
        },
        navigationIcon = {
            if (isLoggedIn && userName.isNotEmpty() && userType != null) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu Icon",
                        tint = Color.Black
                    )
                }
            }
        },
        actions = {
            if (isLoggedIn && userName.isNotEmpty()) {
                IconButton(onClick = onProfileClick) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile Icon",
                        tint = Color.Black
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFFFFF0F0), // 연한 핑크
            titleContentColor = Color.White
        )
    )
}
