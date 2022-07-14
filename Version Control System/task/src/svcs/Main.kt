package svcs

import java.io.File
import java.security.MessageDigest
import java.math.BigInteger
import kotlin.text.StringBuilder

const val VCS_DIR_PATH = "./vcs"
const val COMMITS_DIR_PATH = "commits"
const val CONFIG_FILE_NAME = "config.txt"
const val INDEX_FILE_NAME = "index.txt"
const val LOG_FILE_NAME = "log.txt"
const val HASH_ALGORITHM = "SHA-256"

// Log file format:
// commitId:::commitAuthor:::commitMessage
const val LOG_ENTRY_SEPARATOR = ":::"

val commandMap: Map<String, String> = mapOf(
    "config" to "Get and set a username.",
    "add" to "Add a file to the index.",
    "log" to "Show commit logs.",
    "commit" to "Save changes.",
    "checkout" to "Restore a file."
)

fun main(args: Array<String>) {
    if (args.isEmpty() || args[0] == "--help") {
        printHelpPage()
    } else if (commandMap.containsKey(args[0])) {
        when (args[0]) {
            "config" -> {
                if (args.size == 1) {
                    configure()
                    printUserName()
                } else {
                    configure(args[1])
                }
            }
            "add" -> {
                if (args.size > 1) {
                    addFile(args[1])
                } else {
                    listTrackedFiles()
                }
            }
            "log" -> printCommitLog()
            "commit" -> {
                if (args.size == 1) {
                    println("Message was not passed.")
                } else {
                    val message = trimCommitMessage(args.slice(1..args.lastIndex).joinToString(" "))
                    if (message.isBlank()) {
                        println("Message was not passed.")
                    } else {
                        commitChanges(message)
                    }
                }
            }
            "checkout" -> {
                if (args.size == 1) {
                    println("Commit id was not passed.")
                } else if (commitExists(args[1])) {
                    checkoutCommit(args[1])
                } else {
                    println("Commit does not exist.")
                }
            }
            else -> println(commandMap[args[0]])
        }
    } else {
        println("'${args[0]}' is not a SVCS command.")
    }
}

fun printHelpPage() {
    println("These are SVCS commands:") // 11
    for (command in commandMap) {
        val padding = " ".repeat(11 - command.key.length)
        println("${command.key}$padding${command.value}")
    }
}

fun getVcsDir(): File {
    val vcsDir = File(VCS_DIR_PATH)

    if (!vcsDir.exists()) {
        vcsDir.mkdir()
    }

    return vcsDir
}

fun configure() {
    val vcsDir = getVcsDir()
    val commitsDir = vcsDir.resolve(COMMITS_DIR_PATH)
    val indexFile = vcsDir.resolve(INDEX_FILE_NAME)
    val configFile = vcsDir.resolve(CONFIG_FILE_NAME)
    val logFile = vcsDir.resolve(LOG_FILE_NAME)

    if (!commitsDir.exists()) {
        commitsDir.mkdir()
    }

    if (!indexFile.exists()) {
        indexFile.createNewFile()
    }

    if (!configFile.exists()) {
        configFile.createNewFile()
    }

    if (!logFile.exists()) {
        logFile.createNewFile()
    }
}

fun configure(userName: String) {
    configure()
    val vcsDir = getVcsDir()
    val configFile = vcsDir.resolve(CONFIG_FILE_NAME)

    configFile.writeText(userName)
    println("The username is $userName.")
}

fun printUserName() {
    val vcsDir = getVcsDir()
    val configFile = vcsDir.resolve(CONFIG_FILE_NAME)

    if (!configFile.exists() || configFile.length() == 0L) {
        println("Please, tell me who you are.")
    } else {
        val userName = configFile.readLines()[0]
        println("The username is $userName.")
    }
}

fun addFile(fileName: String) {
    val fileToAdd = File(fileName)
    if (!fileToAdd.exists()) {
        println("Can't find '$fileName'.")
        return
    }

    val vcsDir = getVcsDir()
    val indexFile = vcsDir.resolve(INDEX_FILE_NAME)
    if (!indexFile.exists()) {
        indexFile.createNewFile()
    }

    indexFile.appendText("$fileName\n")
    println("The file '$fileName' is tracked.")
}

fun listTrackedFiles() {
    val vcsDir = getVcsDir()
    val indexFile = vcsDir.resolve(INDEX_FILE_NAME)

    if (indexFile.exists()) {
        val files = indexFile.readLines()
        if (files.isEmpty()) {
            println("Add a file to the index.")
        } else {
            println("Tracked files:")
            files.forEach {
                println(it)
            }
        }
    } else {
        println("Add a file to the index.")
    }
}

fun printCommitLog() {
    val vcsDir = getVcsDir()
    val logFile = vcsDir.resolve(LOG_FILE_NAME)

    if (!logFile.exists() || logFile.length() == 0L) {
        println("No commits yet.")
    } else {
        val lines = logFile.readLines()
        lines.forEach {
            val (commitId, commitAuthor, commitMessage) = it.split(LOG_ENTRY_SEPARATOR)
            println("commit $commitId")
            println("Author: $commitAuthor")
            println(commitMessage)
        }
    }
}

fun shouldCommit(): Boolean {
    val vcsDir = getVcsDir()
    val commitsDir = vcsDir.resolve(COMMITS_DIR_PATH)
    val indexFile = vcsDir.resolve(INDEX_FILE_NAME)
    val logFile = vcsDir.resolve(LOG_FILE_NAME)

    // Are there any files indexed?
    if (indexFile.readLines().isEmpty()) {
        return false
    }

    // Has anything been committed yet?
    if (logFile.readLines().isEmpty() || commitsDir.list().isNullOrEmpty()) {
        return true
    }

    // Were any files added or removed?
    val (latestCommitID, _, _) = logFile.readLines()[0].split(LOG_ENTRY_SEPARATOR)
    val commitDir = commitsDir.resolve(latestCommitID)
    val committedFileNames = commitDir.list()!!.sorted()
    val indexedFileNames = indexFile.readLines().sorted()

    if (committedFileNames != indexedFileNames) {
        return true
    }

    // Has any file been modified?
    for (fileName in committedFileNames) {
        val currentFile = File(fileName)
        val committedFile = commitDir.resolve(fileName)

        val currentFileHash = getHexString(getHash(currentFile.readText()))
        val committedFileHash = getHexString(getHash(committedFile.readText()))

        if (currentFileHash != committedFileHash) {
            return true
        }
    }

    return false
}

fun trimCommitMessage(commitMessage: String): String {
    var message = commitMessage.trim()

    if (message.first() == '"') {
        message = message.drop(1)
    }

    if (message.last() == '"') {
        message.dropLast(1)
    }

    return message.trim()
}

fun commitChanges(commitMessage: String) {
    if (!shouldCommit()) {
        println("Nothing to commit.")
        return
    }

    val vcsDir = getVcsDir()
    val commitsDir = vcsDir.resolve(COMMITS_DIR_PATH)
    if (!commitsDir.exists()) {
        commitsDir.mkdir()
    }

    val logFile = vcsDir.resolve(LOG_FILE_NAME)
    if (!logFile.exists()) {
        logFile.createNewFile()
    }

    val configFile = vcsDir.resolve(CONFIG_FILE_NAME)
    val indexFile = vcsDir.resolve(INDEX_FILE_NAME)
    val userName = configFile.readLines()[0]
    val fileHashStrings = indexFile.readLines().map {
        val file = File(it)
        getHexString(getHash(file.readText()))
    }
    val commitId = createCommitId(fileHashStrings)

    // Create commit directory and copy files
    val commitDir = commitsDir.resolve(commitId)
    commitDir.mkdir()

    indexFile.readLines().forEach {
        val sourceFile = File(it)
        val targetFile = commitDir.resolve(it)

        targetFile.writeBytes(sourceFile.readBytes())
    }

    // Update log file
    val logEntries = logFile.readLines().toMutableList()
    val commitEntry = "$commitId:::$userName:::$commitMessage"
    logEntries.add(0, commitEntry)
    logFile.writeText(logEntries.joinToString("\n"))

    println("Changes are committed.")
}

fun getHash(input: String): ByteArray {
    val messageDigest = MessageDigest.getInstance(HASH_ALGORITHM)

    return messageDigest.digest(input.toByteArray())
}

fun getHexString(bytes: ByteArray): String {
    val number = BigInteger(1, bytes)
    val hexStr = StringBuilder(number.toString(16))

    while (hexStr.length < 64) {
        hexStr.insert(0, '0')
    }

    return hexStr.toString()
}

fun createCommitId(fileHashStrings: List<String>): String {
    return getHexString(getHash(fileHashStrings.joinToString("")))
}

fun commitExists(commitID: String): Boolean {
    val vcsDir = getVcsDir()
    val logFile = vcsDir.resolve(LOG_FILE_NAME)
    val commits = logFile.readLines().map {
        it.split(LOG_ENTRY_SEPARATOR)[0]
    }

    return commits.contains(commitID)
}

fun checkoutCommit(commitID: String) {
    val vcsDir = getVcsDir()
    val commitsDir = vcsDir.resolve(COMMITS_DIR_PATH)
    val commitDir = commitsDir.resolve(commitID)
    val indexFile = vcsDir.resolve(INDEX_FILE_NAME)
    val currentFileNames = indexFile.readLines().sorted()
    val committedFileNames = commitDir.list()!!.sorted()

    // Delete current files
    for (fileName in currentFileNames) {
        val file = File(fileName)
        file.delete()
    }

    // Copy files from the source commit dir
    for (fileName in committedFileNames) {
        val sourceFile = commitDir.resolve(fileName)
        val targetFile = File(fileName)
        targetFile.createNewFile()

        targetFile.writeBytes(sourceFile.readBytes())
    }

    // Update index file
    indexFile.writeText(committedFileNames.joinToString("\n"))

    println("Switched to commit $commitID.")
}
