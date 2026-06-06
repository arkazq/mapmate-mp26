package com.mapmate.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mapmate.presentation.common.EmptyStateCard
import com.mapmate.presentation.common.MapMateSpacing
import com.mapmate.presentation.common.NotificationCircle
import com.mapmate.presentation.common.ScreenHeader
import com.mapmate.ui.theme.MapMateTheme

@Composable
fun RecordsScreen(
    contentPadding: PaddingValues,
    onRegisterRoutineClick: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(
            horizontal = MapMateSpacing.ScreenHorizontal,
            vertical = MapMateSpacing.ScreenTop,
        ),
        verticalArrangement = Arrangement.spacedBy(MapMateSpacing.Section),
    ) {
        item {
            ScreenHeader(
                title = "이동 기록",
                subtitle = "이동 기록은 추후 추천 시간 보정에 사용됩니다.",
                trailingContent = { NotificationCircle() },
            )
        }

        item {
            EmptyStateCard(
                title = "아직 저장된 이동 기록이 없습니다",
                message = "상세 예측에서 이동 기록을 시작하면 기록이 여기에 표시됩니다.",
                actionLabel = "루틴 등록하기",
                onActionClick = onRegisterRoutineClick,
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecordsScreenPreview() {
    MapMateTheme {
        RecordsScreen(
            contentPadding = PaddingValues(),
            onRegisterRoutineClick = {},
        )
    }
}
