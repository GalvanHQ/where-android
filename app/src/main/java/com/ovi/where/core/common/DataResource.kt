package com.ovi.where.core.common

/**
 * Typealias for [com.ovi.where.data.util.Resource] so that domain-layer interfaces
 * can reference it without importing directly from the data package.
 *
 * This keeps the domain layer decoupled from data-layer package structure while
 * still using the same underlying type for NetworkBoundResource state emissions.
 */
typealias DataResource<T> = com.ovi.where.data.util.Resource<T>
