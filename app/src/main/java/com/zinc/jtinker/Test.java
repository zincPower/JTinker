package com.zinc.jtinker;

import android.content.Context;
import android.widget.Toast;

/**
 * @author Jiang zinc
 * @date 创建时间：2018/4/4
 * @description
 */

public class Test {

    public void testFix(Context context) {

        int i = 10;
        int a = 0;
        Toast.makeText(context, "shit:" + i / a, Toast.LENGTH_SHORT).show();

    }

}
