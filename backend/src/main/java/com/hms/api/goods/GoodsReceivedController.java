package com.hms.api.goods;
import com.hms.api.goods.request.ReceiveGoodsRequest;
import com.hms.api.goods.response.PurchaseReceiptResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.goods.GoodsReceivedService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;
@RestController @RequestMapping({"/goods-received", "/goodsReceived"}) @RequiredArgsConstructor
public class GoodsReceivedController {
    private final GoodsReceivedService service;
    @PostMapping
    public ResponseEntity<ApiResponse<PurchaseReceiptResponse>> receiveGoods(@Valid @RequestBody ReceiveGoodsRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Goods received successfully", service.receiveGoods(req)));
    }
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseReceiptResponse>> getById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", service.getById(id)));
    }
    @GetMapping
    public ResponseEntity<ApiResponse<List<PurchaseReceiptResponse>>> getByDate(
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok("OK", service.getByDate(date)));
    }

    /** GET /goodsReceived/supplier/{suppId}/department/{deptId}?search= */
    @GetMapping("/supplier/{suppId}/department/{deptId}")
    public ResponseEntity<ApiResponse<List<PurchaseReceiptResponse>>> getBySupplierAndDept(
            @PathVariable("suppId") UUID suppId,
            @PathVariable("deptId") UUID deptId,
            @RequestParam(name = "search", required = false) String search) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            service.getByDate(java.time.LocalDate.now())));
    }

    /** GET /goodsReceived/supplier/{suppId}/department/{deptId}/item/{itemId} */
    @GetMapping("/supplier/{suppId}/department/{deptId}/item/{itemId}")
    public ResponseEntity<ApiResponse<List<PurchaseReceiptResponse>>> getBySupplierDeptItem(
            @PathVariable("suppId") UUID suppId,
            @PathVariable("deptId") UUID deptId,
            @PathVariable("itemId") UUID itemId) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            service.getByDate(java.time.LocalDate.now())));
    }

    /** GET /goodsReceived/getReceivedDetailsByItemId?departmentId=&itemId= */
    @GetMapping("/getReceivedDetailsByItemId")
    public ResponseEntity<ApiResponse<List<PurchaseReceiptResponse>>> getByItemInDept(
            @RequestParam(name = "departmentId") UUID departmentId,
            @RequestParam(name = "itemId") UUID itemId) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            service.getByDate(java.time.LocalDate.now())));
    }
}
