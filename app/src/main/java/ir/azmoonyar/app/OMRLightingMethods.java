package ir.azmoonyar.app;

/**
 * Experimental OMR lighting comparison. All methods return darkness scores
 * in [0,1] for the inner ellipse of each bubble. Diagnostic only: production
 * grading continues to use BubbleDarknessAnalyzer until a method is validated.
 */
public final class OMRLightingMethods {
    public static final String[] NAMES = {
            "آستانه تطبیقی محلی", "اوتسو", "ساوولا", "کنتراست نرمال‌شده"
    };

    private OMRLightingMethods() {}

    public static double[][] compare(byte[] gray, int width, int height,
            double[][] centers, double radiusX, double radiusY) {
        if (gray == null || width < 3 || height < 3 || (long) width * height > gray.length
                || centers == null || centers.length != 4 || radiusX < 2 || radiusY < 2) {
            throw new IllegalArgumentException("تصویر یا مختصات حباب‌ها معتبر نیست.");
        }
        int otsu = otsu(gray, width, height);
        double[][] result = new double[4][4];
        for (int option = 0; option < 4; option++) {
            double cx = centers[option][0], cy = centers[option][1];
            double rx = radiusX * 0.58, ry = radiusY * 0.58;
            int x0 = Math.max(0, (int)Math.floor(cx-rx));
            int x1 = Math.min(width-1, (int)Math.ceil(cx+rx));
            int y0 = Math.max(0, (int)Math.floor(cy-ry));
            int y1 = Math.min(height-1, (int)Math.ceil(cy+ry));
            long count=0, adaptiveDark=0, otsuDark=0, sauvolaDark=0;
            double localSum=0, localSq=0, outerSum=0; long outerCount=0;
            int localRadius = Math.max(5, (int)Math.round(Math.min(rx,ry)*0.75));
            for(int y=y0;y<=y1;y++) for(int x=x0;x<=x1;x++) {
                double dx=(x-cx)/rx, dy=(y-cy)/ry;
                if(dx*dx+dy*dy>1) continue;
                int v=gray[y*width+x]&255;
                int[] stats=windowStats(gray,width,height,x,y,localRadius);
                double mean=stats[0], std=Math.sqrt(Math.max(0,stats[1]-mean*mean));
                double adaptiveThreshold=mean-5.0;
                double sauvolaThreshold=mean*(1.0+0.22*(std/128.0-1.0));
                if(v<adaptiveThreshold) adaptiveDark++;
                if(v<otsu) otsuDark++;
                if(v<sauvolaThreshold) sauvolaDark++;
                localSum+=v; localSq+=(double)v*v; count++;
            }
            // A surrounding annulus estimates paper illumination; dark ink is
            // expressed as the normalized drop from the local paper brightness.
            double outerR2=1.75;
            int ox0=Math.max(0,(int)(cx-rx*1.8)), ox1=Math.min(width-1,(int)(cx+rx*1.8));
            int oy0=Math.max(0,(int)(cy-ry*1.8)), oy1=Math.min(height-1,(int)(cy+ry*1.8));
            for(int y=oy0;y<=oy1;y++) for(int x=ox0;x<=ox1;x++) {
                double dx=(x-cx)/rx,dy=(y-cy)/ry, d=dx*dx+dy*dy;
                if(d<=1.15 || d>outerR2) continue;
                outerSum += gray[y*width+x]&255; outerCount++;
            }
            if(count==0) continue;
            double meanBubble=localSum/count;
            double meanPaper=outerCount==0?255.0:outerSum/outerCount;
            result[0][option]=adaptiveDark/(double)count;
            result[1][option]=otsuDark/(double)count;
            result[2][option]=sauvolaDark/(double)count;
            result[3][option]=clamp((meanPaper-meanBubble)/Math.max(35.0,meanPaper));
        }
        return result;
    }

    private static int[] windowStats(byte[] a,int w,int h,int x,int y,int r) {
        long sum=0,sq=0,n=0;
        for(int yy=Math.max(0,y-r);yy<=Math.min(h-1,y+r);yy+=2)
            for(int xx=Math.max(0,x-r);xx<=Math.min(w-1,x+r);xx+=2) {
                int v=a[yy*w+xx]&255; sum+=v; sq+=(long)v*v; n++;
            }
        double m=sum/(double)Math.max(1,n);
        return new int[]{(int)Math.round(m),(int)Math.round(sq/(double)Math.max(1,n))};
    }

    private static int otsu(byte[] a,int w,int h) {
        long[] hist=new long[256]; long total=(long)w*h, sum=0;
        for(int i=0;i<total;i++){int v=a[i]&255;hist[v]++;sum+=(long)v;}
        long bg=0,bgSum=0; double best=-1; int threshold=127;
        for(int t=0;t<256;t++){
            bg+=hist[t]; if(bg==0)continue;
            long fg=total-bg; if(fg==0)break;
            bgSum+=(long)t*hist[t];
            double m1=bgSum/(double)bg,m2=(sum-bgSum)/(double)fg;
            double between=(double)bg*fg*(m1-m2)*(m1-m2);
            if(between>best){best=between;threshold=t;}
        }
        return threshold;
    }
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
}
