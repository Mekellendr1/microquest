package com.example.microquest.friends

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.microquest.network.ApiClient
import com.example.microquest.network.FriendDto
import com.example.microquest.network.FriendRequestDto
import com.example.microquest.network.LeaderboardEntry
import com.example.microquest.network.QuestFeedItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    vm: FriendsViewModel,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var addUsername by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it); vm.clearMessage() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Друзья") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Default.Refresh, "Обновить")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.PersonAdd, "Добавить друга")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            val requestBadge = state.incomingRequests.size
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("Друзья (${state.friends.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = {
                        BadgedBox(badge = {
                            if (requestBadge > 0) Badge { Text("$requestBadge") }
                        }) { Text("Запросы") }
                    })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                    text = { Text("Лента") })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 },
                    text = { Text("Топ") })
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (selectedTab) {
                    0 -> FriendsTab(state.friends, vm::removeFriend)
                    1 -> RequestsTab(state.incomingRequests, vm::acceptRequest, vm::declineRequest)
                    2 -> FeedTab(state.feed, vm::vote)
                    3 -> LeaderboardTab(state.leaderboard)
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false; addUsername = "" },
            title = { Text("Добавить друга") },
            text = {
                OutlinedTextField(
                    value = addUsername,
                    onValueChange = { addUsername = it },
                    label = { Text("Имя пользователя (@username)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("friends_username_input")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addFriend(addUsername)
                    showAddDialog = false
                    addUsername = ""
                }) { Text("Отправить") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; addUsername = "" }) {
                    Text("Отмена")
                }
            }
        )
    }
}


@Composable
private fun FriendsTab(friends: List<FriendDto>, onRemove: (String) -> Unit) {
    if (friends.isEmpty()) {
        EmptyState("Пока нет друзей", "Нажми + чтобы добавить друга по имени пользователя")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(friends, key = { it.friendshipId }) { friend ->
            FriendCard(friend = friend, onRemove = { onRemove(friend.friendshipId) })
        }
    }
}

@Composable
private fun FriendCard(friend: FriendDto, onRemove: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(friend.displayName.firstOrNull()?.uppercase() ?: "?",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(friend.displayName, fontWeight = FontWeight.SemiBold)
                Text("@${friend.username}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Ур. ${friend.level}  •  ${friend.xp} XP  •  ${friend.completedCount} квестов",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { showConfirm = true }) {
                Icon(Icons.Default.PersonRemove, "Удалить",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Удалить из друзей?") },
            text = { Text("@${friend.username} будет удалён из твоего списка друзей.") },
            confirmButton = {
                TextButton(onClick = { onRemove(); showConfirm = false }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Отмена") }
            }
        )
    }
}


@Composable
private fun RequestsTab(
    requests: List<FriendRequestDto>,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit
) {
    if (requests.isEmpty()) {
        EmptyState("Нет входящих запросов", "Когда кто-то добавит тебя в друзья, запрос появится здесь")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(requests, key = { it.friendshipId }) { req ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(req.from.displayName.firstOrNull()?.uppercase() ?: "?",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(req.from.displayName, fontWeight = FontWeight.SemiBold)
                        Text("@${req.from.username}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Ур. ${req.from.level}  •  ${req.from.completedCount} квестов",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onAccept(req.friendshipId) }) {
                        Icon(Icons.Default.Check, "Принять",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { onDecline(req.friendshipId) }) {
                        Icon(Icons.Default.Close, "Отклонить",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}


@Composable
private fun FeedTab(feed: List<QuestFeedItem>, onVote: (String, Boolean) -> Unit) {
    if (feed.isEmpty()) {
        EmptyState("Лента пуста", "Здесь будут квесты друзей, которые ждут твоей оценки")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(feed, key = { it.questId }) { item ->
            FeedCard(item = item, onVote = onVote)
        }
    }
}

@Composable
private fun FeedCard(item: QuestFeedItem, onVote: (String, Boolean) -> Unit) {
    val questEmoji = when (item.questType) {
        "ACTION" -> "🏃"; "VOICE" -> "🎙️"; "PHOTO" -> "📷"; else -> "✏️"
    }
    val statusColor = when (item.status) {
        "VERIFIED" -> MaterialTheme.colorScheme.primary
        "REJECTED" -> MaterialTheme.colorScheme.error
        else       -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusLabel = when (item.status) {
        "VERIFIED" -> "✓ Подтверждено"; "REJECTED" -> "✗ Отклонено"; else -> "⏳ На проверке"
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(item.displayName.firstOrNull()?.uppercase() ?: "?",
                            fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.displayName, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium)
                    Text("@${item.username}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor)
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(questEmoji, fontSize = 18.sp)
                    Text(item.questText, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f))
                }
            }

            if (!item.mediaUrl.isNullOrBlank()) {
                val baseUrl = ApiClient.BASE_URL.trimEnd('/')
                AsyncImage(
                    model = "$baseUrl${item.mediaUrl}",
                    contentDescription = "Proof media",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }

            if (!item.proofText.isNullOrBlank()) {
                Text("💬 ${item.proofText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("+${item.xpEarned} XP",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold)

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = { onVote(item.questId, true) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (item.myVote == true)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("👍 ${item.approvals}",
                            color = if (item.myVote == true)
                                MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalButton(
                        onClick = { onVote(item.questId, false) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (item.myVote == false)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("👎 ${item.rejections}",
                            color = if (item.myVote == false)
                                MaterialTheme.colorScheme.onError
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}


@Composable
private fun LeaderboardTab(entries: List<LeaderboardEntry>) {
    if (entries.isEmpty()) {
        EmptyState("Лидерборд пуст", "Добавь друзей и выполняй квесты, чтобы соревноваться!")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(entries, key = { _, e -> e.userId }) { index, entry ->
            LeaderboardRow(rank = index + 1, entry = entry)
        }
    }
}

@Composable
private fun LeaderboardRow(rank: Int, entry: LeaderboardEntry) {
    val medal = when (rank) {
        1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#$rank"
    }
    val cardColor = if (entry.isMe)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = cardColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(medal,
                    fontSize = if (rank <= 3) 24.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (rank > 3) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified)
            }

            Surface(shape = CircleShape,
                color = if (entry.isMe) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        entry.displayName.firstOrNull()?.uppercase() ?: "?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (entry.isMe) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.displayName, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium)
                    if (entry.isMe) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text("Ты", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
                Text("Ур. ${entry.level}  •  ${entry.completedCount} квестов",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text("${entry.xp}", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text("XP", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


@Composable
private fun EmptyState(title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp)) {
            Text("🙈", fontSize = 48.sp)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
