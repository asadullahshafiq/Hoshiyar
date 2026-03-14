package com.assistant.personal.core

object DictionaryHelper {

    // Offline dictionary - common words
    private val dictionary = mapOf(
        // Common English words
        "happy" to "Feeling or showing pleasure or contentment. Khush, Masroor.",
        "sad" to "Feeling sorrow or unhappiness. Udaas, Ghamgeen.",
        "love" to "Strong affection for someone. Mohabbat, Pyaar.",
        "peace" to "Freedom from disturbance or war. Amn, Sukoon.",
        "success" to "Achievement of a goal or aim. Kamyabi, Saffalta.",
        "failure" to "Lack of success. Naakamyabi, Hazemat.",
        "beautiful" to "Pleasing the senses or mind. Khoobsurat, Haseen.",
        "strong" to "Having great power or force. Mazboot, Taqatwar.",
        "weak" to "Lacking strength or power. Kamzor, Naatawan.",
        "brave" to "Ready to face danger with courage. Bahadur, Diler.",
        "honest" to "Free of deceit, truthful. Imaandaar, Sachcha.",
        "patience" to "Ability to wait without annoyance. Sabr.",
        "wisdom" to "Experience and knowledge. Hikmaat, Aql.",
        "trust" to "Firm belief in someone. Bharosa, Aitbaar.",
        "hope" to "Feeling of expectation and desire. Umeed.",
        "dream" to "A series of thoughts during sleep. Khwaab.",
        "goal" to "Aim or desired result. Maqsad, Hadaf.",
        "money" to "Currency used for exchange. Paisa, Raqam.",
        "time" to "Indefinite progress of existence. Waqt, Zamana.",
        "family" to "Group of related people. Khandan, Ghar.",
        "friend" to "Person with mutual affection. Dost, Yaar.",
        "enemy" to "Person hostile to another. Dushman.",
        "knowledge" to "Facts and information acquired. Ilm, Maloomat.",
        "education" to "Process of learning. Taleem.",
        "health" to "State of being free from illness. Sehat.",
        "wealth" to "Abundance of valuable resources. Daulat.",
        "power" to "Ability to do something. Taqat, Qudrat.",
        "freedom" to "Power to act without constraint. Azaadi.",
        "justice" to "Quality of being fair. Insaaf, Adl.",
        "truth" to "That which is in accordance with fact. Sach, Haq.",
        "lie" to "False statement. Jhooth.",
        "fear" to "Unpleasant emotion from danger. Dar, Khauf.",
        "courage" to "Ability to do something scary. Himmat, Jurrat.",
        "kindness" to "Quality of being friendly and generous. Mehrbaani.",
        "anger" to "Strong feeling of annoyance. Gussa, Ghazab.",
        "joy" to "A feeling of great pleasure. Khushi, Mussarrat.",
        "pain" to "Physical or mental suffering. Dard, Takleef.",
        "life" to "Existence of a living being. Zindagi, Hayat.",
        "death" to "Permanent end of life. Maut, Wafaat.",
        "heart" to "Organ pumping blood; seat of emotion. Dil.",
        "mind" to "Element that thinks and feels. Zehan, Dimagh.",
        "soul" to "Spiritual part of a being. Rooh.",
        "sky" to "Region of atmosphere above earth. Aasman.",
        "earth" to "Planet we live on; soil. Zameen.",
        "water" to "Clear liquid essential for life. Paani.",
        "fire" to "Rapid oxidation producing heat. Aag.",
        "wind" to "Moving air. Hawa.",
        "light" to "Electromagnetic radiation visible to eye. Roshni, Noor.",
        "darkness" to "Absence of light. Andheera, Zulmat.",
        "silence" to "Complete absence of sound. Khamoshi.",
        "voice" to "Sound made by human when speaking. Awaaz.",
        "word" to "Unit of language. Lafz, Shabd.",
        "book" to "Written or printed work. Kitaab.",
        "world" to "The earth and all its people. Duniya.",
        "universe" to "All existing matter and space. Kaainaat.",
        "god" to "Supreme being. Allah, Khuda.",
        "prayer" to "Act of communicating with God. Dua, Ibadat.",
        "faith" to "Complete trust or confidence. Iman, Yaqeen.",
        "respect" to "Deep admiration for someone. Izzat, Ehtiraam.",
        "honor" to "High respect and esteem. Izzat, Sharaf.",
        "shame" to "Painful feeling of humiliation. Sharm, Haya.",
        "pride" to "Feeling of deep satisfaction. Fakhr, Guroor.",
        "humble" to "Having a modest opinion of oneself. Mutawadda, Inkisaar.",
        "generous" to "Showing readiness to give. Sakhi, Fiyyaaz.",
        "selfish" to "Concerned only with oneself. Khudgharj.",
        "loyal" to "Giving firm support. Wafadaar.",
        "betray" to "To be disloyal to. Dhoka dena.",
        "forgive" to "Stop feeling angry at someone. Maaf karna.",
        "forget" to "Fail to remember. Bhool jaana.",
        "remember" to "Have in mind. Yaad rakhna.",
        "learn" to "Gain knowledge or skill. Seekhna.",
        "teach" to "Show or explain. Sikhana, Padhana.",
        "work" to "Activity involving effort. Kaam, Mehnat.",
        "rest" to "Cease work to relax. Aaram.",
        "sleep" to "Natural periodic state of rest. Neend.",
        "eat" to "Put food in mouth. Khana.",
        "drink" to "Take liquid into mouth. Peena.",
        "run" to "Move at speed. Daurna.",
        "walk" to "Move at regular pace. Chalna.",
        "talk" to "Speak to convey information. Baat karna.",
        "listen" to "Give attention to sound. Sunna.",
        "see" to "Perceive with eyes. Dekhna.",
        "think" to "Have a particular opinion. Sochna.",
        "feel" to "Experience an emotion. Mehsoos karna.",
        "help" to "Make it easier for someone. Madad karna.",
        "hurt" to "Cause physical or mental pain. Takleef dena.",
        "smile" to "Form pleased expression. Muskurana.",
        "cry" to "Shed tears. Rona.",
        "win" to "Be successful. Jeetna.",
        "lose" to "Fail to win. Haarna.",
        "give" to "Freely transfer to another. Dena.",
        "take" to "Lay hold of. Lena.",
        "start" to "Begin. Shuru karna.",
        "stop" to "Come to an end. Rokna, Band karna.",
        "continue" to "Carry on. Jaari rakhna.",
        "change" to "Make or become different. Badalna.",
        "grow" to "Increase in size. Barhna.",
        "fall" to "Move downward. Girna.",
        "rise" to "Move upward. Uthna.",
        "build" to "Construct. Banana.",
        "destroy" to "Put an end to. Tabah karna.",
        "create" to "Bring into existence. Paida karna.",
        "imagine" to "Form a mental image. Tasawwur karna.",
        "believe" to "Accept as true. Maanna.",
        "doubt" to "Feel uncertain. Shak karna.",
        "understand" to "Perceive the meaning. Samajhna.",
        "explain" to "Make clear. Samjhana.",
        "question" to "Ask about something. Sawal.",
        "answer" to "Reply to a question. Jawaab."
    )

    fun lookup(word: String): String? {
        val w = word.lowercase().trim()
        return dictionary[w]
    }

    fun isLookupRequest(text: String): Boolean {
        val t = text.lowercase()
        return t.startsWith("meaning of") ||
            t.startsWith("what is") ||
            t.startsWith("define") ||
            t.startsWith("dictionary") ||
            t.contains("ka matlab") ||
            t.contains("meaning")
    }

    fun extractWord(text: String): String {
        return text.lowercase()
            .replace("meaning of", "")
            .replace("what is", "")
            .replace("define", "")
            .replace("dictionary", "")
            .replace("ka matlab", "")
            .replace("meaning", "")
            .replace("?", "")
            .trim()
    }
}
