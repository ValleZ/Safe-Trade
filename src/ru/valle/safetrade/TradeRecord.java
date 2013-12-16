/**
 The MIT License (MIT)

 Copyright (c) 2013 Valentin Konovalov

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.*/
package ru.valle.safetrade;

import android.text.TextUtils;

public final class TradeRecord {
    public static final int TYPE_SELL = 0;
    public static final int TYPE_BUY = 1;
    public final long id;
    public final String name, password, intermediateCode, confirmationCode, address, destinationAddress, encryptedPrivateKey, privateKey;
    public final int type;
    public final long balance;
    public final boolean itemReceived;

    public TradeRecord(long id, int type, String name, String password, String intermediateCode, String confirmationCode,
                       String address, String destinationAddress, String encryptedPrivateKey, String privateKey, long balance, boolean itemReceived) {
        this.id = id;
        this.name = name;
        this.password = password;
        this.intermediateCode = intermediateCode;
        this.confirmationCode = confirmationCode;
        this.address = address;
        this.destinationAddress = destinationAddress;
        this.encryptedPrivateKey = encryptedPrivateKey;
        this.privateKey = privateKey;
        this.type = type;
        this.balance = balance;
        this.itemReceived = itemReceived;
    }

    public int getStatus() {
        if (type == TYPE_SELL) {
            if (TextUtils.isEmpty(confirmationCode)) {
                return R.string.waiting_for_confirmation_code;
            }
            if (balance < 0) {
                return R.string.checking_balance;
            }
            if (TextUtils.isEmpty(encryptedPrivateKey)) {
                return R.string.waiting_private_key;
            }
            if (balance > 0) {
                return R.string.you_should_take_funds;
            }
            return R.string.successfully_sold;
        } else if (type == TYPE_BUY) {
            if (balance < 0) {
                return R.string.checking_balance_buyer;
            }
            if (balance == 0 && !itemReceived) {
                return R.string.deposit_funds;
            }
            if (!itemReceived) {
                return R.string.waiting_item;
            }
            return R.string.all_set;

        }
        return R.string.status_unknown;
    }
}
