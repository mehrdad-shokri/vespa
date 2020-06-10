// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function.h"
#include "prepare_result.h"
#include <vespa/eval/tensor/dense/typed_cells.h>
#include <vespa/vespalib/util/generationhandler.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <cstdint>
#include <memory>
#include <vector>

namespace vespalib::slime { struct Inserter; }

namespace search::fileutil { class LoadedBuffer; }

namespace search { class BitVector; }

namespace search::tensor {

class NearestNeighborIndexSaver;

/**
 * Interface for an index that is used for (approximate) nearest neighbor search.
 */
class NearestNeighborIndex {
public:
    using generation_t = vespalib::GenerationHandler::generation_t;
    struct Neighbor {
        uint32_t docid;
        double distance;
        Neighbor(uint32_t id, double dist)
          : docid(id), distance(dist)
        {}
        Neighbor() : docid(0), distance(0.0) {}
    };
    virtual ~NearestNeighborIndex() {}
    virtual void add_document(uint32_t docid) = 0;

    /**
     * Performs the prepare step in a two-phase operation to add a document to the index.
     *
     * This function can be called by any thread.
     * The document to add is represented by the given vector as it is _not_ stored in the enclosing tensor attribute at this point in time.
     * It should return the result of the costly and non-modifying part of this operation.
     * The given read guard must be kept in the result.
     */
    virtual std::unique_ptr<PrepareResult> prepare_add_document(uint32_t docid,
                                                                vespalib::tensor::TypedCells vector,
                                                                vespalib::GenerationHandler::Guard read_guard) const {
        // TODO: Make it pure virtual after more wiring is complete.
        (void) docid;
        (void) vector;
        (void) read_guard;
        return std::unique_ptr<PrepareResult>();
    }

    /**
     * Performs the complete step in a two-phase operation to add a document to the index.
     *
     * This function is only called by the attribute writer thread.
     * It uses the result from the prepare step to do the modifying changes.
     */
    virtual void complete_add_document(uint32_t docid,
                                       std::unique_ptr<PrepareResult> prepare_result) {
        // TODO: Make it pure virtual after more wiring is complete.
        (void) docid;
        (void) prepare_result;
    }

    virtual void remove_document(uint32_t docid) = 0;
    virtual void transfer_hold_lists(generation_t current_gen) = 0;
    virtual void trim_hold_lists(generation_t first_used_gen) = 0;
    virtual vespalib::MemoryUsage memory_usage() const = 0;
    virtual void get_state(const vespalib::slime::Inserter& inserter) const = 0;

    /**
     * Creates a saver that is used to save the index to binary form.
     *
     * This function is always called by the attribute write thread,
     * and the caller ensures that an attribute read guard is held during the lifetime of the saver.
     */
    virtual std::unique_ptr<NearestNeighborIndexSaver> make_saver() const = 0;
    virtual bool load(const fileutil::LoadedBuffer& buf) = 0;

    virtual std::vector<Neighbor> find_top_k(uint32_t k,
                                             vespalib::tensor::TypedCells vector,
                                             uint32_t explore_k) const = 0;

    // only return neighbors where the corresponding filter bit is set
    virtual std::vector<Neighbor> find_top_k_with_filter(uint32_t k,
                                                         vespalib::tensor::TypedCells vector,
                                                         const BitVector &filter,
                                                         uint32_t explore_k) const = 0;

    virtual const DistanceFunction *distance_function() const = 0;
};

}
