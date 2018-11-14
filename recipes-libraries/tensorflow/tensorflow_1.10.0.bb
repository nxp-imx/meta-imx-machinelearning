DESCRIPTION = "TensorFlow C/C++ Libraries"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=01e86893010a1b87e69a213faa753ebd"

DEPENDS = "bazel-native protobuf-native protobuf"
RDEPENDS_${PN}-dev += "libeigen-dev protobuf-dev"
PACKAGES += "${PN}-examples ${PN}-examples-dbg"

S = "${WORKDIR}/git"

SRCREV = "656e7a2b347c3c6eb76a6c130ed4b1def567b6c1"
BRANCH = "r1.10"

SRC_URI = " \
	git://github.com/tensorflow/tensorflow.git;branch=${BRANCH} \
	file://CROSSTOOL.patch \
	file://yocto-crosscompile.patch \
	file://Fix-all-compilation-errors-from-undefined-functions-in-j.patch \
	file://Fix-alignment-issue-when-neon-is-enabled.patch \
	file://Remove-python-support-from-label_image-example-applicati.patch \
	file://Patching-eigen-library-on-the-fly-via-bazel.patch \
	file://Patching-nsync-library-on-the-fly-via-bazel.patch \
	file://Fix-eigen-header-compiler-crash.patch;apply=no \
	file://Fix-alignment-issue-in-arm-neon-platform.patch;apply=no \
"

COMPATIBLE_MACHINE = "(imx8qm)"

export BAZEL_FLAGS="--output_base ${WORKDIR}/output_base"

do_configure () {
	CT_DIR="${STAGING_DIR_NATIVE}"
	# maybe use TARGET_SYS instead?
	CT_NAME=$(echo ${HOST_PREFIX} | rev | cut -c 2- | rev)
	GCC_VERSION=$(${CC} -dumpversion)

	SED_COMMAND="s#%%CT_NAME%%#${CT_NAME}#g"
	SED_COMMAND="${SED_COMMAND}; s#%%CT_ROOT_DIR%%#${CT_DIR}#g"
	SED_COMMAND="${SED_COMMAND}; s#%%CT_GCC_VERSION%%#${GCC_VERSION}#g"
	SED_COMMAND="${SED_COMMAND}; s#%%CT_STAGING_DIR%%#${STAGING_DIR_HOST}#g"

	# apply changes on placeholders in files: WORKSPACE, yocto.BUILD, tools/yocto/BUILD, tools/yocto/CROSSTOOL
	cd ${S}
	find . -not -path "*/\.*" -type f \( -path "*yocto*" -o -name "WORKSPACE" \) \
		-exec sed -i "${SED_COMMAND}" '{}' \;

	# Bazel's attempts to autocongure toolchain get confused, need to unset following variables
	unset $(printenv | cut -d "=" -f1 | grep -v '^PATH$')

	mkdir -p ${WORKDIR}/output_base

	export JAVA_HOME=${STAGING_BINDIR_NATIVE}/openjdk-1.8-native
	(TF_NEED_JEMALLOC=0 \
	 TF_NEED_GCP=0 \
	 TF_NEED_CUDA=0 \
	 TF_NEED_S3=0 \
	 TF_NEED_HDFS=0 \
	 TF_NEED_KAFKA=0 \
	 TF_NEED_OPENCL_SYCL=0 \
	 TF_NEED_OPENCL=0 \
	 TF_CUDA_CLANG=0 \
	 TF_DOWNLOAD_CLANG=0 \
	 TF_ENABLE_XLA=0 \
	 TF_NEED_GDR=0 \
	 TF_NEED_VERBS=0 \
	 TF_NEED_MPI=0 \
	 TF_NEED_AWS=0 \
	 TF_SET_ANDROID_WORKSPACE=0 \
	./configure)

	cp ${WORKDIR}/Fix-eigen-header-compiler-crash.patch ${S}/third_party/
	cp ${WORKDIR}/Fix-alignment-issue-in-arm-neon-platform.patch ${S}/third_party/
}

do_compile () {
	export HTTP_PROXY=${http_proxy}
	export HTTPS_PROXY=${https_proxy}

	# Bazel's attempts to autocongure toolchain get confused, need to unset following variables
	unset $(printenv | cut -d "=" -f1 | grep -Ev '^PATH$|^BAZEL_FLAGS$|^HTTP_PROXY$|^HTTPS_PROXY$')

	export JAVA_HOME=${STAGING_BINDIR_NATIVE}/openjdk-1.8-native

	bazel $BAZEL_FLAGS build \
		--config=monolithic \
		-c opt \
		--copt=-DPLATFORM_POSIX \
		--cpu=yocto\
		--crosstool_top=//tools/yocto:toolchain \
		--verbose_failures \
		tensorflow:libtensorflow.so

        bazel $BAZEL_FLAGS build \
		--config=monolithic \
		-c opt \
		--copt=-DPLATFORM_POSIX \
		--cpu=yocto \
		--crosstool_top=//tools/yocto:toolchain \
		--verbose_failures \
		tensorflow/examples/label_image/...
}

do_install () {
	install -d ${D}${libdir}

	install -m 0555 \
		${S}/bazel-bin/tensorflow/libtensorflow*.so \
		${D}${libdir}

	install -d ${D}${includedir}/tensorflow
	cd ${S}/bazel-genfiles/tensorflow
	cp --parents \
		$(find . -name "*.h*") \
		${D}${includedir}/tensorflow
	rm -rf \
		${D}${includedir}/tensorflow/core/example

	install -d ${D}/usr/src/debug/tensorflow
	cp --parents \
		$(find . -type f \( -iname \*.c* -o -iname \*.h* \) ) \
		${D}/usr/src/debug/tensorflow

	install -d ${D}${bindir}/${PN}-${PV}/examples

	install -m 0555 \
		${S}/bazel-bin/tensorflow/examples/label_image/label_image \
		${D}${bindir}/${PN}-${PV}/examples

	install -m 0644 \
		${S}/tensorflow/examples/label_image/data/grace_hopper.jpg \
		${D}${bindir}/${PN}-${PV}/examples
	cd ${D}${bindir}
	ln -sf ${PN}-${PV} ${PN}
}

INSANE_SKIP_${PN} += " \
	already-stripped \
"

FILES_${PN} = " \
	${libdir} \
"

FILES_${PN}-dev = " \
	${includedir} \
"

FILES_${PN}-dbg = " \
	${libdir}/.debug \
	/usr/src/debug/tensorflow \
"

FILES_${PN}-examples = " \
	${bindir}/${PN} \
	${bindir}/${PN}-${PV}/examples/label_image \
	${bindir}/${PN}-${PV}/examples/grace_hopper.jpg \
"

FILES_${PN}-examples-dbg = " \
	${bindir}/${PN}-${PV}/examples/.debug \
"
