package gnu.io

fun ensurePortIsClosed(port: CommPortIdentifier) {
    port.internalClosePort()
}