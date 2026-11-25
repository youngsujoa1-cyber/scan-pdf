"""생성된 PDF 를 MuPDF(PyMuPDF)로 다시 열어 구조와 렌더링을 검사한다.

사용: python3 tools/logic-check/verify.py
"""
import sys
import fitz  # PyMuPDF

failures = 0


def check(name, actual, expected):
    global failures
    if actual == expected:
        print(f"PASS  {name} = {actual}")
    else:
        failures += 1
        print(f"FAIL  {name} = {actual} (expected {expected})")


def check_true(name, condition):
    check(name, bool(condition), True)


def main():
    created = fitz.open("tools/logic-check/out/created.pdf")
    check("created pages", created.page_count, 3)
    check("created is PDF", created.is_pdf, True)
    check("created encrypted", created.is_encrypted, False)

    p0 = created[0]
    w, h = p0.rect.width, p0.rect.height
    check_true("page0 portrait", h > w)
    check("page0 long side ~A4", round(h, 1), 841.9)
    check("page0 aspect", round(w / h, 4), round(1240 / 1754, 4))

    p1 = created[1]
    check_true("page1 landscape", p1.rect.width > p1.rect.height)
    check("page1 long side ~A4", round(p1.rect.width, 1), 841.9)

    images = p0.get_images(full=True)
    check("page0 has one image", len(images), 1)
    # DCTDecode(JPEG) 로 들어갔는지
    for img in images:
        xref = img[0]
        info = created.extract_image(xref)
        check("image is jpeg", info["ext"], "jpeg")
        check("image width", info["width"], 1240)
        check("image height", info["height"], 1754)

    # 렌더링이 실제로 되는지 (회색 종이 + 검은 글자)
    pix = p0.get_pixmap(dpi=72)
    check_true("page0 renders", pix.width > 100 and pix.height > 100)
    samples = pix.samples
    dark = sum(1 for i in range(0, len(samples), 3) if samples[i] < 80)
    check_true("page0 has dark text pixels", dark > 100)

    text = created.metadata.get("title")
    check("title", text, "스캔 문서 테스트")
    check("producer", created.metadata.get("producer"), "ScanPDF")

    bw = fitz.open("tools/logic-check/out/bw.pdf")
    check("bw pages", bw.page_count, 2)
    bw_images = bw[0].get_images(full=True)
    check("bw page has image", len(bw_images), 1)
    bw_info = bw.extract_image(bw_images[0][0])
    check("bw image is jpeg", bw_info["ext"], "jpeg")
    print(f"info  sizes: created={__import__('os').path.getsize('tools/logic-check/out/created.pdf')} bytes, "
          f"bw={__import__('os').path.getsize('tools/logic-check/out/bw.pdf')} bytes")

    created.close()
    bw.close()
    print()
    if failures == 0:
        print("PYMUPDF VERIFY PASSED")
    else:
        print(f"{failures} FAILURE(S)")
        sys.exit(1)


if __name__ == "__main__":
    main()
