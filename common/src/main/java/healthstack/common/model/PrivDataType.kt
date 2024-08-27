package healthstack.common.model

enum class PrivDataType(val messagePath: String, val isPassive: Boolean = false) {
    WEAR_ACCELEROMETER("", true),
    WEAR_ECG("/ecg_data"),
    WEAR_HEART_RATE("", true),
    WEAR_PPG_GREEN("", true),
    WEAR_PPG_RED("", true),
}
