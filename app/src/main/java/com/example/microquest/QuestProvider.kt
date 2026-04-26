package com.example.microquest.data

/**
 * Static catalogue of micro-quests.
 * Add / remove freely — IDs must stay unique.
 */
object QuestProvider {

    val all: List<Quest> = listOf(

        // ── ACTION ──────────────────────────────────────────────────────
        Quest(1,  "Сделай 10 приседаний прямо сейчас",                      QuestType.ACTION),
        Quest(2,  "Выпей стакан воды залпом",                               QuestType.ACTION),
        Quest(3,  "Потянись и достань до пола (или попробуй)",               QuestType.ACTION),
        Quest(4,  "Закрой глаза и сделай 5 глубоких вдохов",                QuestType.ACTION),
        Quest(5,  "Встань и попрыгай на месте 20 раз",                      QuestType.ACTION),
        Quest(6,  "Помассируй виски 30 секунд",                              QuestType.ACTION),
        Quest(7,  "Улыбнись максимально широко и удержи 10 секунд",         QuestType.ACTION),
        Quest(8,  "Сделай 5 отжиманий (или от стены — тоже считается)",     QuestType.ACTION),
        Quest(9,  "Пройдись по комнате задом наперёд",                      QuestType.ACTION),
        Quest(10, "Потри ладони до тепла и приложи к лицу",                 QuestType.ACTION),
        Quest(11, "Встань на одну ногу и простой 30 секунд",                QuestType.ACTION),
        Quest(12, "Похлопай в ладоши ровно 30 раз",                         QuestType.ACTION),

        // ── TEXT ────────────────────────────────────────────────────────
        Quest(13, "Напиши в заметках одно хорошее, что случилось сегодня",  QuestType.TEXT),
        Quest(14, "Придумай и запиши нелепое название для группы",          QuestType.TEXT),
        Quest(15, "Назови вслух 5 вещей, которые ты видишь прямо сейчас",   QuestType.TEXT),
        Quest(16, "Напиши одно слово, которое описывает твоё настроение",   QuestType.TEXT),
        Quest(17, "Запиши имя человека, которому хочешь сказать спасибо",   QuestType.TEXT),
        Quest(18, "Придумай смешной заголовок для несуществующей статьи",   QuestType.TEXT),
        Quest(19, "Напиши 3 слова, которые ассоциируются с летом",          QuestType.TEXT),
        Quest(20, "Вспомни и запиши последний сон, который помнишь",        QuestType.TEXT),
        Quest(21, "Запиши одну вещь, которую давно откладываешь",           QuestType.TEXT),
        Quest(22, "Придумай кличку для домашнего растения (или кота)",      QuestType.TEXT),

        // ── PHOTO ───────────────────────────────────────────────────────
        Quest(23, "Сфотографируй что-то синего цвета рядом с тобой",        QuestType.PHOTO),
        Quest(24, "Сделай фото своей тени",                                 QuestType.PHOTO),
        Quest(25, "Сфотографируй самый интересный угол в комнате",          QuestType.PHOTO),
        Quest(26, "Сфотографируй свои руки крупным планом",                 QuestType.PHOTO),
        Quest(27, "Найди и сфотографируй что-то круглое",                   QuestType.PHOTO),
        Quest(28, "Сфотографируй вид из ближайшего окна",                   QuestType.PHOTO),
        Quest(29, "Сделай фото чего-то, что тебя сейчас вдохновляет",      QuestType.PHOTO),
        Quest(30, "Сфотографируй любую букву, найденную в окружении",       QuestType.PHOTO),
        Quest(31, "Сфотографируй что-то старое и что-то новое в одном кадре", QuestType.PHOTO),

        // ── EXTRA ACTION ────────────────────────────────────────────────
        Quest(32, "Скажи вслух три вещи, за которые ты благодарен",         QuestType.ACTION),
        Quest(33, "Найди в комнате 5 предметов одного цвета",               QuestType.ACTION),
        Quest(34, "Потяни шею влево-вправо по 5 раз в каждую сторону", QuestType.ACTION),
        // ── VOICE ────────────────────────────────────────────────────────────
        Quest(35, "Скажи вслух три вещи, за которые ты благодарен сегодня",   QuestType.VOICE),
        Quest(36, "Расскажи голосом самый смешной случай из этой недели",      QuestType.VOICE),
        Quest(37, "Произнеси скороговорку «Карл у Клары» три раза подряд",    QuestType.VOICE),
        Quest(38, "Запиши голосовое — опиши свой день одним предложением",     QuestType.VOICE),
        Quest(39, "Скажи вслух имена пяти людей, которые тебе важны",         QuestType.VOICE),
        Quest(40, "Расскажи голосом, чего ждёшь от этой недели",              QuestType.VOICE),
    )

    /** Returns a random quest excluding already-completed IDs. Null if all done. */
    fun random(excludeIds: Set<Int>): Quest? {
        val available = all.filter { it.id !in excludeIds }
        return available.randomOrNull()
    }
}
