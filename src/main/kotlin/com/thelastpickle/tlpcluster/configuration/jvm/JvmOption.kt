package com.thelastpickle.tlpcluster.configuration.jvm

sealed class JvmOption {

    abstract fun isKeyEqual(j: JvmOption) : Boolean

    enum class HeapFlag { MAX_HEAP, NEW_GEN, STARTING_HEAP, UNKNOWN }

    /**
     * -Xmx16G
     */
    data class HeapOption(val key: HeapFlag, val value: String) : JvmOption() {
        override fun isKeyEqual(j: JvmOption): Boolean {
            return (j is HeapOption) && (this.key.ordinal == j.key.ordinal)
        }
    }

    /**
     * -ea # enable assertions
     */
    data class Flag(val value: String) : JvmOption() {
        override fun toString() = "-$value"
        override fun isKeyEqual(j: JvmOption): Boolean {
            return (j is Flag && value == j.value)
        }
    }

    /**
     * -XX:StringTableSize=1000002
     * -Xss255k
     */
    data class Pair(val key: String, val value: String) : JvmOption() {
        override fun toString() = "-$key=$value"
        override fun isKeyEqual(j: JvmOption): Boolean {
            return (j is Pair && key == j.key)
        }
    }

    /**
     * -DSTUFF=WHATEVER
     */
    data class DynamicOption(val name: String, val value: String) : JvmOption() {
        override fun toString() = "-D$name=$value"
        override fun isKeyEqual(j: JvmOption): Boolean {
            return (j is DynamicOption && name == j.name)
        }

    }

    /**
     * -XX:-UseBiasedLocking
     * -XX:+HeapDumpOnOutOfMemoryError
     */
    data class BooleanOption(val name: String, val enabled: Boolean) : JvmOption() {
        override fun toString() = "-XX:" + (if(enabled) "+" else "-") + name
        override fun isKeyEqual(j: JvmOption): Boolean {
            return (j is BooleanOption && name == j.name)
        }
    }

    data class Unknown(val value: String) : JvmOption() {
        override fun toString() = value
        override fun isKeyEqual(j: JvmOption): Boolean = false
    }


    companion object {
        fun parse(line: String) : JvmOption {

            val heapParser = "-Xm([xsn])([0-9]+[gmGm])".toRegex()
            val boolParser = "-XX:([+-])(\\w+)".toRegex()
            val pairParser = "-(.*)=(.*)".toRegex()
            val dynamicParser = "-D(\\S+)=(\\S+)".toRegex()
            val simpleParser = "-(\\S+)".toRegex()

            heapParser.find(line)?.run {
                val option = when(groupValues[1]) {
                    "x" -> HeapFlag.MAX_HEAP
                    "n" -> HeapFlag.NEW_GEN
                    "s" -> HeapFlag.STARTING_HEAP
                    else -> HeapFlag.UNKNOWN
                }
                return HeapOption(option, groupValues[2])
            }
            boolParser.find(line)?.run {
                return BooleanOption(groupValues[2], groupValues[1] == "+")
            }
            dynamicParser.find(line)?.run {
                return DynamicOption(groupValues[1], groupValues[2])
            }
            pairParser.find(line)?.run {
                return Pair(groupValues[1], groupValues[2])
            }
            simpleParser.find(line)?.run {
                return Flag(groupValues[1])
            }

            return Unknown(line)
        }
    }
}