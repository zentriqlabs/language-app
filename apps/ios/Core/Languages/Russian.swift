import Foundation

extension LanguageModule {
    /// Standard Russian for learners; regional variants are accepted in teaching policy.
    public static let russian = LanguageModule(
        id: "ru", name: "Russian", nativeName: "Русский", variety: "Russia", locale: "ru-RU",
        greeting: "Привет!", greetingWord: "привет",
        speechGuidance: "Use clear, natural Standard Russian pronunciation. Use ты for friendly conversation and вы when the situation calls for formality. Model stress, vowel reduction and consonant softening naturally. Accept valid regional accents and vocabulary without treating regional variation or a non-native accent alone as an error. Do not infer a pronunciation error from spelling alone.",
        writingGuidance: "Use standard Russian spelling, ё where appropriate, and modern punctuation. Accept valid regional wording while keeping replies natural for contemporary Russia.",
        lemmaGuidance: "Give nouns in the nominative singular without an article and verbs in the infinitive, for example дом and говорить. Preserve aspect pairs when relevant. Keep reflexive verbs such as называться distinct. Quote the learner's exact form.",
        teachingFocus: [
            "Greetings, introductions and short everyday chunks such as меня зовут and я хочу.",
            "Everyday questions, gender and case in simple phrases, present tense and useful motion verbs.",
            "Connected stories, past tense, aspect contrasts and familiar situations.",
            "Reasons and opinions, subordinate clauses, polite requests and natural connectors.",
            "Nuance, hypotheticals, idiomatic phrasing and register.",
            "Flexible advanced discussion with precise, natural Russian."
        ],
        topicPlaceholder: "Food, travel, music, life in Russia…",
        lookupUnavailableReply: "Сейчас я не смог это проверить. Если хочешь, можем поговорить о теме в общем.",
        themeOverrides: [
            "coffee": .init("coffee", "Кофе?", "Something warm, please", "cup.and.saucer", "Everyday", "Meet in a neighbourhood café in Russia. Order a drink and chat naturally. Follow the learner's interests.", 0),
            "groceries": .init("groceries", "На рынке", "Find something good", "basket", "Everyday", "Shop at a local market in Russia. Practise quantities, prices and polite requests.", 2),
            "travel": .init("travel", "В путь", "A ticket to somewhere", "tram", "Everyday", "Plan a trip in Russia. Discuss transport and tickets without inventing current schedules.", 1),
            "cabin": .init("cabin", "На выходные", "A quieter kind of day", "mountain.2", "Local life", "Plan an imagined weekend trip in Russia. Choose a city, countryside or coast together.", 2),
            "traditions": .init("traditions", "За столом", "Small customs, big stories", "flag", "Local life", "Talk about everyday customs in Russia with nuance. Avoid treating all Russian speakers as alike.", 2)
        ]
    )
}
