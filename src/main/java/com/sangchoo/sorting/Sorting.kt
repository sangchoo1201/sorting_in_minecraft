package com.sangchoo.sorting

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

abstract class Sortable {
    var sortEnded: Boolean = false
    abstract var step: Double
    private var progress: Double = 0.0
    private var index: Int = 0

    val data
        get() = Sorting.instance.data

    fun advance() {
        var result = false
        while (progress < 1 && !result) {
            progress += step
            result = sort()
        }
        progress -= 1
        if (result) {
            Sorting.instance.apply {
                server.scheduler.cancelTasks(this)
            }
        }
    }

    fun complete(): Boolean {
        step = 40.0 / data.size
        Sorting.setColumn(index, data[index], "minecraft:green_concrete")
        index++
        if (index == data.size) {
            Sorting.instance.apply {
                server.scheduler.cancelTasks(this)
            }
            return true
        }
        return false
    }

    abstract fun sort(): Boolean
}

class BubbleSort(override var step: Double) : Sortable() {
    private var swap = false
    private var p = 0
    private var right = data.size - 1

    override fun sort(): Boolean {
        if (sortEnded) {
            return complete()
        }

        if (swap) {
            val temp = data[p]
            data[p] = data[p + 1]
            data[p + 1] = temp
            swap = false

            Sorting.setColumn(p, data[p], "minecraft:red_concrete")
            Sorting.setColumn(p + 1, data[p + 1], "minecraft:red_concrete")
            return false
        }

        for (i in data.withIndex()) {
            Sorting.setColumn(i.index, i.value, "minecraft:white_concrete")
        }

        p++
        if (p == right) {
            right--
            p = 0
            if (right == 0) {
                sortEnded = true
                return false
            }
        }

        Sorting.setColumn(p, data[p], "minecraft:red_concrete")
        Sorting.setColumn(p + 1, data[p + 1], "minecraft:red_concrete")

        if (data[p] > data[p + 1]) {
            swap = true
        }
        return false
    }
}

class BogoSort(override var step: Double) : Sortable() {
    private var p = 0

    override fun sort(): Boolean {
        if (sortEnded) {
            return complete()
        }

        if (p == data.size - 1) {
            sortEnded = true
            return false
        }

        if (data[p] > data[p + 1]) {
            p = 0
            data.shuffle()
            for (i in data.withIndex()) {
                Sorting.setColumn(i.index, i.value, "minecraft:white_concrete")
            }
            return false
        }

        Sorting.setColumn(p, data[p], "minecraft:green_concrete")
        Sorting.setColumn(p + 1, data[p + 1], "minecraft:red_concrete")
        p++
        return false
    }
}

class InsertionSort(override var step: Double) : Sortable() {
    private var p = 0
    private var q = 0
    private var temp = 0

    override fun sort(): Boolean {
        if (sortEnded) {
            return complete()
        }

        for (i in data.withIndex()) {
            Sorting.setColumn(i.index, i.value, "minecraft:white_concrete")
        }

        p++
        if (p == data.size) {
            sortEnded = true
            return false
        }

        Sorting.setColumn(p, data[p], "minecraft:red_concrete")

        q = p
        temp = data[p]
        while (q > 0 && data[q - 1] > temp) {
            data[q] = data[q - 1]
            Sorting.setColumn(q, data[q], "minecraft:red_concrete")
            q--
        }
        data[q] = temp
        Sorting.setColumn(q, data[q], "minecraft:red_concrete")
        return false
    }
}

class Sorting : JavaPlugin() {
    companion object {
        lateinit var instance: Sorting
            private set

        fun setColumn(x: Int, height: Int, block: String = "") {
            instance.apply {
                for (y in 0 until height) {
                    server.worlds[0].setBlockData(x, y + 100, 0, server.createBlockData(block))
                }
                for (y in height until data.size) {
                    server.worlds[0].setBlockData(x, y + 100, 0, server.createBlockData("minecraft:air"))
                }
            }
        }
    }

    init {
        instance = this
    }

    var data = Array(30) { it + 1 }
    private val sorts = mutableMapOf(
        "bubble" to (BubbleSort::class.java).getConstructor(Double::class.java),
        "bogo" to (BogoSort::class.java).getConstructor(Double::class.java),
    )

    override fun onEnable() {
        var x = 0
        while (!server.worlds[0].getBlockData(x, 100, 0).material.isAir) {
            x++
        }
        data = Array(x) { it + 1 }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String>? {
        return when (command.name) {
            "sort" -> {
                if (args.size == 1) sorts.keys.toMutableList() else MutableList(0) { "" }
            }

            else -> super.onTabComplete(sender, command, alias, args)
        }
    }

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        return when (command.name) {
            "stopsort" -> {
                server.scheduler.cancelTasks(this)
                true
            }

            "shuffle" -> {
                server.scheduler.cancelTasks(this)
                shuffleData()
                true
            }

            "setdatasize" -> {
                server.scheduler.cancelTasks(this)
                return setDataSize(args)
            }

            "sort" -> if (args.isNotEmpty()) startSort(args) else false
            else -> super.onCommand(sender, command, label, args)
        }
    }

    private fun setDataSize(args: Array<out String>): Boolean {
        if (args.size != 1 || args[0].toIntOrNull() == null) return false
        for (i in data.indices) {
            setColumn(i, 0)
        }
        data = Array(args[0].toInt()) { it + 1 }
        shuffleData()
        return true
    }

    private fun shuffleData() {
        data = data.toList().shuffled().toTypedArray()
        for (i in data.withIndex()) {
            setColumn(i.index, i.value, "minecraft:white_concrete")
        }
    }

    private fun startSort(args: Array<out String>): Boolean {
        data = data.toList().shuffled().toTypedArray()
        logger.info("Sorting ${args[0]}")
        logger.info("data is ${data.joinToString(", ", "[", "]")}")

        var step = 1.0
        if (args.size >= 2) {
            if (args[1].toDoubleOrNull() != null) {
                step = args[1].toDouble()
            } else {
                logger.info("period must be a number")
                return false
            }
        }

        if (args[0] !in sorts.keys) return false
        val sort = sorts[args[0]]!!.newInstance(step)

        for (i in data.withIndex()) {
            setColumn(i.index, i.value, "minecraft:white_concrete")
        }

        server.scheduler.scheduleSyncRepeatingTask(this, sort::advance, 0, 1)
        return true
    }
}