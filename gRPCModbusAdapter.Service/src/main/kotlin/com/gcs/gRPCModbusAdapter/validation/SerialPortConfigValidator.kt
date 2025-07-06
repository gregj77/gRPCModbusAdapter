package com.gcs.gRPCModbusAdapter.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [BaudRateValidator::class])
annotation class BaudRateConstraint(
    val message: String = "Baud rate outside of supported range [1200, 2400, 4800 or 9600]",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class BaudRateValidator : ConstraintValidator<BaudRateConstraint, Int> {
    override fun isValid(input: Int, ctx: ConstraintValidatorContext): Boolean
        = arrayOf(1200, 2400, 4800, 9600).contains(input)
}

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [DataBitsValidator::class])
annotation class DataBitsConstraint(
    val message: String = "Data bits outside of supported range [5,6,7 or 8]",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class DataBitsValidator : ConstraintValidator<DataBitsConstraint, Int> {
    override fun isValid(input: Int, ctx: ConstraintValidatorContext): Boolean
            = arrayOf(5, 6, 7, 8).contains(input)
}

